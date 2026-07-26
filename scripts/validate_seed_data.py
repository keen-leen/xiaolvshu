#!/usr/bin/env python3
"""独立检查已生成 SQL，防止绕过生成阶段的质量门禁。"""

from __future__ import annotations

import json
import re
from collections import Counter
from pathlib import Path


BASE_DIR = Path(__file__).resolve().parent
SQL_FILE = BASE_DIR.parent / "docker" / "dev" / "mysql" / "init" / "data.sql"
REPORT_FILE = BASE_DIR / "seed_data" / "quality_report.json"
FORBIDDEN_CONTENT_MARKERS = ("演示笔记", "模拟笔记", "演示数据", "模拟数据", "Pexels API")


def extract_values(pattern: str, sql: str) -> list[tuple[str, ...]]:
    return re.findall(pattern, sql, flags=re.MULTILINE)


def main() -> None:
    sql = SQL_FILE.read_text(encoding="utf-8")
    posts = extract_values(
        r"INSERT INTO posts .*?VALUES \((\d+), \d+, '((?:\\'|[^'])*)', '((?:\\'|[^'])*)', (\d+),",
        sql,
    )
    images = extract_values(
        r"INSERT INTO post_images .*?VALUES \((\d+), '([^']+)', \d+, '([^']+)', '([^']+)',",
        sql,
    )
    comments = extract_values(
        r"INSERT INTO comments .*?VALUES \(\d+, (\d+), \d+, NULL, '((?:\\'|[^'])*)',",
        sql,
    )

    errors: list[str] = []
    if len(posts) != 500:
        errors.append(f"帖子数应为 500，实际为 {len(posts)}")
    if len({title for _, title, _, _ in posts}) != len(posts):
        errors.append("SQL 中存在重复标题")
    if len({content for _, _, content, _ in posts}) != len(posts):
        errors.append("SQL 中存在重复正文")
    polluted_posts = [
        post_id
        for post_id, title, content, _ in posts
        if any(marker in title or marker in content for marker in FORBIDDEN_CONTENT_MARKERS)
    ]
    if polluted_posts:
        errors.append(f"笔记正文含有展示数据标记：{polluted_posts[:10]}")
    category_counts = Counter(int(category_id) for *_, category_id in posts)
    if len(category_counts) != 10 or min(category_counts.values(), default=0) < 40:
        errors.append(f"分类分布不合格：{dict(category_counts)}")

    image_counts = Counter(int(post_id) for post_id, *_ in images)
    if len(image_counts) != 500 or any(not 2 <= count <= 4 for count in image_counts.values()):
        errors.append("并非每篇帖子都有2—4张图片")
    if any(provider != "pexels" for _, _, provider, _ in images):
        errors.append("SQL 中混入非 Pexels 图片")
    image_ids = [asset_id for *_, asset_id in images]
    duplicate_rate = 1 - len(set(image_ids)) / len(image_ids) if image_ids else 1
    if duplicate_rate >= 0.05:
        errors.append(f"图片重复率 {duplicate_rate:.2%} 超过 5%")
    comment_counts = Counter(int(post_id) for post_id, _ in comments)
    if (
        len(comment_counts) != 500
        or any(not 4 <= count <= 8 for count in comment_counts.values())
        or len({content for _, content in comments}) != len(comments)
    ):
        errors.append("每篇帖子必须有4—8条不重复的大模型评论")
    if any(
        marker in content
        for _, content in comments
        for marker in FORBIDDEN_CONTENT_MARKERS
    ):
        errors.append("评论含有展示数据标记")

    if not REPORT_FILE.exists():
        errors.append("缺少 quality_report.json")
    else:
        report = json.loads(REPORT_FILE.read_text(encoding="utf-8"))
        if (
            report.get("status") != "passed"
            or report.get("provider") != "pexels"
            or report.get("unique_comments") != report.get("comments")
        ):
            errors.append("质量报告未通过或图片来源不是 Pexels")

    if errors:
        raise SystemExit("种子数据检查失败：\n- " + "\n- ".join(errors))
    print(
        f"种子数据检查通过：{len(posts)} 篇帖子，{len(comments)} 条评论，"
        f"{len(images)} 张 Pexels 图片，"
        f"图片重复率 {duplicate_rate:.2%}"
    )


if __name__ == "__main__":
    main()
