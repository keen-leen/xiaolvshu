#!/usr/bin/env python3
"""生成目的地驱动、图文一致且可复现的公开演示数据。"""

from __future__ import annotations

import csv
import json
import random
from collections import Counter, defaultdict
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any


BASE_DIR = Path(__file__).resolve().parent
DESTINATIONS_FILE = BASE_DIR / "seed_data" / "destinations.csv"
USERS_FILE = BASE_DIR / "seed_data" / "users.json"
POST_MEDIA_MANIFEST_FILE = BASE_DIR / "seed_data" / "post_media_manifest.json"
AVATAR_MEDIA_MANIFEST_FILE = BASE_DIR / "seed_data" / "avatar_media_manifest.json"
GENERATED_POSTS_FILE = BASE_DIR / "seed_data" / "generated_posts.json"
OUTPUT_FILE = BASE_DIR.parent / "docker" / "dev" / "mysql" / "init" / "data.sql"
QUALITY_REPORT_FILE = BASE_DIR / "seed_data" / "quality_report.json"

RANDOM_SEED = 20260725
REFERENCE_TIME = datetime(2026, 7, 1, 12, 0, 0)
POSTS_PER_DESTINATION = 10
EXPECTED_DESTINATIONS = 50
EXPECTED_POSTS = EXPECTED_DESTINATIONS * POSTS_PER_DESTINATION
DEFAULT_PASSWORD_BCRYPT = "$2a$12$81U/nCucOHrJRPeGpZXFRONN07x8wYndkqsZ7Hm5M6Xx3PbFr1kA6"

CATEGORIES = [
    ("国内游", "domestic"),
    ("出境游", "abroad"),
    ("自驾游", "roadtrip"),
    ("徒步登山", "hiking"),
    ("海岛度假", "island"),
    ("美食探店", "food"),
    ("民宿酒店", "hotel"),
    ("摄影打卡", "photography"),
    ("穷游攻略", "budget"),
    ("亲子游", "family"),
]

FORBIDDEN_CONTENT_MARKERS = ("演示笔记", "模拟笔记", "演示数据", "模拟数据", "Pexels API")

def sql_escape(value: Any) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, float)):
        return str(value)
    text = str(value).replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
    return f"'{text}'"


class SeedGenerator:
    def __init__(self) -> None:
        self.rng = random.Random(RANDOM_SEED)
        self.destinations = self.load_destinations()
        self.users = self.load_users()
        self.post_media_manifest = self.load_post_media_manifest()
        self.avatar_media_manifest = self.load_avatar_media_manifest()
        self.category_ids = {name: index for index, (name, _) in enumerate(CATEGORIES, start=1)}
        self.sql: list[str] = []

    def load_destinations(self) -> list[dict[str, Any]]:
        with DESTINATIONS_FILE.open(encoding="utf-8", newline="") as file:
            rows = list(csv.DictReader(file, delimiter="|"))
        if len(rows) != EXPECTED_DESTINATIONS:
            raise ValueError(f"目的地必须为 {EXPECTED_DESTINATIONS} 条，当前为 {len(rows)} 条")
        return rows

    def load_users(self) -> list[dict[str, Any]]:
        if not USERS_FILE.exists():
            raise FileNotFoundError(
                f"缺少 {USERS_FILE}；原用户资料必须作为独立输入保留"
            )
        users = json.loads(USERS_FILE.read_text(encoding="utf-8")).get("users") or []
        if len(users) != EXPECTED_DESTINATIONS:
            raise ValueError(f"用户资料必须恰好为50条，当前为 {len(users)} 条")
        if [int(user["id"]) for user in users] != list(range(1, EXPECTED_DESTINATIONS + 1)):
            raise ValueError("用户ID必须连续且为1—50")
        return users

    def load_post_media_manifest(self) -> dict[str, Any]:
        if not POST_MEDIA_MANIFEST_FILE.exists():
            raise FileNotFoundError(
                f"缺少 {POST_MEDIA_MANIFEST_FILE}；请先运行 "
                "python3 scripts/refresh_seed_post_media.py"
            )
        manifest = json.loads(POST_MEDIA_MANIFEST_FILE.read_text(encoding="utf-8"))
        if manifest.get("provider") != "pexels":
            raise ValueError("笔记图片清单只能包含Pexels API资源")
        for destination in self.destinations:
            assets = manifest.get("destinations", {}).get(destination["key"], [])
            if len(assets) < POSTS_PER_DESTINATION * 4:
                raise ValueError(f"{destination['name']} 至少需要40张Pexels图片")
            if any(asset.get("provider") != "pexels" for asset in assets):
                raise ValueError(f"{destination['name']} 混入了非 Pexels 图片")
        return manifest

    def load_avatar_media_manifest(self) -> dict[str, Any]:
        if not AVATAR_MEDIA_MANIFEST_FILE.exists():
            raise FileNotFoundError(
                f"缺少 {AVATAR_MEDIA_MANIFEST_FILE}；请先运行 "
                "python3 scripts/refresh_seed_avatar_media.py"
            )
        manifest = json.loads(AVATAR_MEDIA_MANIFEST_FILE.read_text(encoding="utf-8"))
        avatars = manifest.get("avatars") or []
        if manifest.get("provider") != "pexels" or len(avatars) < EXPECTED_DESTINATIONS:
            raise ValueError("头像清单必须包含至少50张Pexels图片")
        if any(avatar.get("provider") != "pexels" for avatar in avatars):
            raise ValueError("头像清单混入了非Pexels图片")
        return manifest

    def line(self, statement: str = "") -> None:
        self.sql.append(statement + (";" if statement and not statement.startswith("--") else "") + "\n")

    def build_users(self) -> list[dict[str, Any]]:
        avatars = self.avatar_media_manifest["avatars"]
        users = []
        for index, original in enumerate(self.users):
            avatar = avatars[index]
            users.append(
                {
                    **original,
                    "avatar": avatar["thumbnail_url"],
                    "avatar_source_url": avatar["source_url"],
                    "avatar_photographer": avatar["photographer"],
                    "avatar_photographer_url": avatar["photographer_url"],
                    "verified": 0,
                }
            )
        return users

    def build_posts(self) -> list[dict[str, Any]]:
        if not GENERATED_POSTS_FILE.exists():
            raise FileNotFoundError(
                f"缺少 {GENERATED_POSTS_FILE}；请先运行 python3 scripts/generate_seed_content.py"
            )
        generated_payload = json.loads(GENERATED_POSTS_FILE.read_text(encoding="utf-8"))
        generated_posts = generated_payload.get("posts") or []
        if len(generated_posts) != EXPECTED_POSTS:
            raise ValueError(f"大模型内容必须恰好为 {EXPECTED_POSTS} 篇，当前为 {len(generated_posts)} 篇")
        generated_by_destination: defaultdict[str, list[dict[str, Any]]] = defaultdict(list)
        for generated in generated_posts:
            generated_by_destination[str(generated.get("destination_key"))].append(generated)

        posts: list[dict[str, Any]] = []
        for destination_index, destination in enumerate(self.destinations):
            category_id = self.category_ids[destination["category"]]
            destination_posts = sorted(
                generated_by_destination[destination["key"]],
                key=lambda item: int(item["angle_index"]),
            )
            if len(destination_posts) != POSTS_PER_DESTINATION:
                raise ValueError(f"{destination['name']} 必须包含 10 篇大模型内容")
            for generated in destination_posts:
                tags = list(dict.fromkeys(
                    str(tag).strip()
                    for tag in generated.get("tags") or []
                    if str(tag).strip()
                ))[:5]
                if not 3 <= len(tags) <= 5:
                    raise ValueError(f"{destination['name']} 的标签数量必须为3—5个")
                posts.append(
                    {
                        "id": len(posts) + 1,
                        "user_id": destination_index + 1,
                        "title": str(generated["title"]).strip(),
                        "content": str(generated["content"]).strip(),
                        "category_id": category_id,
                        "destination_key": destination["key"],
                        "tags": tags,
                        "comments": generated["comments"],
                        "view_count": self.rng.randint(80, 9800),
                        "created_at": REFERENCE_TIME
                        - timedelta(days=self.rng.randint(0, 150), minutes=self.rng.randint(0, 1439)),
                    }
                )
        return posts

    def build_post_images(self, posts: list[dict[str, Any]]) -> list[dict[str, Any]]:
        offsets: defaultdict[str, int] = defaultdict(int)
        images: list[dict[str, Any]] = []
        for post in posts:
            assets = self.post_media_manifest["destinations"][post["destination_key"]]
            start = offsets[post["destination_key"]]
            image_count = self.rng.randint(2, 4)
            chosen = assets[start : start + image_count]
            offsets[post["destination_key"]] += image_count
            for order, asset in enumerate(chosen):
                images.append(
                    {
                        "post_id": post["id"],
                        "sort_order": order,
                        **asset,
                    }
                )
        return images

    def build_follows(self, user_count: int, count: int = 350) -> list[tuple[int, int]]:
        candidates = [(a, b) for a in range(1, user_count + 1) for b in range(1, user_count + 1) if a != b]
        return self.rng.sample(candidates, count)

    def build_comments(self, posts: list[dict[str, Any]]) -> list[dict[str, Any]]:
        comments: list[dict[str, Any]] = []
        for post in posts:
            for content in post["comments"]:
                comments.append(
                    {
                        "id": len(comments) + 1,
                        "post_id": post["id"],
                        "user_id": self.rng.randint(1, EXPECTED_DESTINATIONS),
                        "content": content,
                        "like_count": self.rng.randint(0, 25),
                        "created_at": post["created_at"] + timedelta(hours=self.rng.randint(1, 240)),
                    }
                )
        return comments

    def build_unique_pairs(self, left_max: int, right_max: int, count: int) -> list[tuple[int, int]]:
        pairs: set[tuple[int, int]] = set()
        while len(pairs) < count:
            pair = (self.rng.randint(1, left_max), self.rng.randint(1, right_max))
            pairs.add(pair)
        return sorted(pairs)

    def validate(
        self,
        posts: list[dict[str, Any]],
        images: list[dict[str, Any]],
        tag_use: Counter[str],
    ) -> dict[str, Any]:
        category_counts = Counter(post["category_id"] for post in posts)
        title_count = len({post["title"] for post in posts})
        content_count = len({post["content"] for post in posts})
        image_ids = [(image["provider"], image["provider_asset_id"]) for image in images]
        per_post = Counter(image["post_id"] for image in images)
        comments = [comment for post in posts for comment in post["comments"]]

        errors: list[str] = []
        if len(posts) != EXPECTED_POSTS:
            errors.append(f"帖子数应为 {EXPECTED_POSTS}，实际为 {len(posts)}")
        if title_count != len(posts):
            errors.append("存在完全重复标题")
        if content_count != len(posts):
            errors.append("存在完全重复正文")
        if any(count < 40 for count in category_counts.values()) or len(category_counts) != 10:
            errors.append(f"分类覆盖不合格：{dict(category_counts)}")
        if any(count < 2 or count > 4 for count in per_post.values()) or len(per_post) != len(posts):
            errors.append("每篇笔记必须有2—4张图片")
        duplicate_rate = 1 - len(set(image_ids)) / len(image_ids)
        if duplicate_rate >= 0.05:
            errors.append(f"图片重复率 {duplicate_rate:.2%} 超过 5%")
        if any(image["provider"] != "pexels" for image in images):
            errors.append("发现非 Pexels 图片")
        polluted_posts = [
            post["id"]
            for post in posts
            if any(marker in post["title"] or marker in post["content"] for marker in FORBIDDEN_CONTENT_MARKERS)
        ]
        if polluted_posts:
            errors.append(f"笔记正文含有展示数据标记：{polluted_posts[:10]}")
        if (
            any(not 4 <= len(post["comments"]) <= 8 for post in posts)
            or len(set(comments)) != len(comments)
        ):
            errors.append("每篇笔记必须有4—8条不重复的大模型评论")
        if any(marker in comment for comment in comments for marker in FORBIDDEN_CONTENT_MARKERS):
            errors.append("评论含有展示数据标记")
        required_media_fields = {
            "source_url",
            "photographer",
            "photographer_url",
            "license_name",
            "license_url",
            "alt_text",
        }
        if any(not all(image.get(field) for field in required_media_fields) for image in images):
            errors.append("图片来源或署名字段不完整")
        if errors:
            raise ValueError("种子数据质量检查失败：\n- " + "\n- ".join(errors))

        return {
            "random_seed": RANDOM_SEED,
            "posts": len(posts),
            "unique_titles": title_count,
            "unique_contents": content_count,
            "category_counts": dict(sorted(category_counts.items())),
            "post_images": len(images),
            "unique_post_images": len(set(image_ids)),
            "image_duplicate_rate": duplicate_rate,
            "tag_count": len(tag_use),
            "comments": len(comments),
            "unique_comments": len(set(comments)),
            "provider": "pexels",
            "status": "passed",
        }

    def generate(self) -> None:
        users = self.build_users()
        posts = self.build_posts()
        post_images = self.build_post_images(posts)
        follows = self.build_follows(len(users))
        comments = self.build_comments(posts)
        post_likes = self.build_unique_pairs(len(users), len(posts), 2200)
        comment_likes = self.build_unique_pairs(len(users), len(comments), 450)
        collections = self.build_unique_pairs(len(users), len(posts), 900)

        comment_counts = Counter(comment["post_id"] for comment in comments)
        post_like_counts = Counter(post_id for _, post_id in post_likes)
        collect_counts = Counter(post_id for _, post_id in collections)
        follow_counts = Counter(follower for follower, _ in follows)
        fan_counts = Counter(following for _, following in follows)
        user_like_counts = Counter()
        for _, post_id in post_likes:
            user_like_counts[posts[post_id - 1]["user_id"]] += 1

        tag_use = Counter(tag for post in posts for tag in post["tags"])
        tags = [{"id": index, "name": name, "use_count": count} for index, (name, count) in enumerate(sorted(tag_use.items()), start=1)]
        tag_ids = {tag["name"]: tag["id"] for tag in tags}

        report = self.validate(posts, post_images, tag_use)

        self.line("-- 小旅书初始化数据；文本由DeepSeek生成，关系由固定随机种子分配")
        self.line("SET NAMES utf8mb4")
        self.line("SET FOREIGN_KEY_CHECKS = 0")
        for table in (
            "user_sessions", "notifications", "comments", "collections", "likes", "post_tags",
            "follows", "post_images", "post_videos", "posts", "tags", "users", "admin", "categories", "audit",
        ):
            self.line(f"TRUNCATE TABLE {table}")
        self.line("SET FOREIGN_KEY_CHECKS = 1")

        for admin_id, username in enumerate(("admin", "admin2", "admin3"), start=1):
            self.line(
                "INSERT INTO admin (id, username, password) VALUES "
                f"({admin_id}, {sql_escape(username)}, {sql_escape(DEFAULT_PASSWORD_BCRYPT)})"
            )

        for category_id, (name, title) in enumerate(CATEGORIES, start=1):
            post_count = sum(1 for post in posts if post["category_id"] == category_id)
            self.line(
                "INSERT INTO categories (id, name, category_title, post_count) VALUES "
                f"({category_id}, {sql_escape(name)}, {sql_escape(title)}, {post_count})"
            )

        for user in users:
            post_count = sum(1 for post in posts if post["user_id"] == user["id"])
            self.line(
                "INSERT INTO users "
                "(id, user_id, password, nickname, avatar, avatar_source_url, avatar_photographer, "
                "avatar_photographer_url, bio, location, follow_count, fans_count, like_count, post_count, "
                "is_active, last_login_at, gender, zodiac_sign, mbti, education, major, interests, verified) VALUES "
                f"({user['id']}, {sql_escape(user['user_id'])}, {sql_escape(user['password'])}, "
                f"{sql_escape(user['nickname'])}, {sql_escape(user['avatar'])}, {sql_escape(user['avatar_source_url'])}, "
                f"{sql_escape(user['avatar_photographer'])}, {sql_escape(user['avatar_photographer_url'])}, "
                f"{sql_escape(user['bio'])}, {sql_escape(user['location'])}, {follow_counts[user['id']]}, "
                f"{fan_counts[user['id']]}, {user_like_counts[user['id']]}, {post_count}, "
                f"{user['is_active']}, {sql_escape(user['last_login_at'])}, {sql_escape(user['gender'])}, "
                f"{sql_escape(user['zodiac_sign'])}, {sql_escape(user['mbti'])}, "
                f"{sql_escape(user['education'])}, {sql_escape(user['major'])}, "
                f"{sql_escape(user['interests'])}, {user['verified']})"
            )

        for tag in tags:
            self.line(
                "INSERT INTO tags (id, name, use_count) VALUES "
                f"({tag['id']}, {sql_escape(tag['name'])}, {tag['use_count']})"
            )

        for post in posts:
            self.line(
                "INSERT INTO posts "
                "(id, user_id, title, content, category_id, type, is_draft, view_count, like_count, "
                "collect_count, comment_count, created_at) VALUES "
                f"({post['id']}, {post['user_id']}, {sql_escape(post['title'])}, {sql_escape(post['content'])}, "
                f"{post['category_id']}, 1, 0, {post['view_count']}, {post_like_counts[post['id']]}, "
                f"{collect_counts[post['id']]}, {comment_counts[post['id']]}, "
                f"{sql_escape(post['created_at'].strftime('%Y-%m-%d %H:%M:%S'))})"
            )

        for image in post_images:
            self.line(
                "INSERT INTO post_images "
                "(post_id, image_url, sort_order, provider, provider_asset_id, photographer, photographer_url, "
                "source_url, license_name, license_url, alt_text) VALUES "
                f"({image['post_id']}, {sql_escape(image['image_url'])}, {image['sort_order']}, "
                f"{sql_escape(image['provider'])}, {sql_escape(image['provider_asset_id'])}, "
                f"{sql_escape(image['photographer'])}, {sql_escape(image['photographer_url'])}, "
                f"{sql_escape(image['source_url'])}, {sql_escape(image['license_name'])}, "
                f"{sql_escape(image['license_url'])}, {sql_escape(image['alt_text'])})"
            )

        for post in posts:
            for tag_name in post["tags"]:
                self.line(
                    "INSERT INTO post_tags (post_id, tag_id) VALUES "
                    f"({post['id']}, {tag_ids[tag_name]})"
                )

        for follower_id, following_id in follows:
            self.line(f"INSERT INTO follows (follower_id, following_id) VALUES ({follower_id}, {following_id})")

        for comment in comments:
            self.line(
                "INSERT INTO comments "
                "(id, post_id, user_id, parent_id, content, like_count, created_at) VALUES "
                f"({comment['id']}, {comment['post_id']}, {comment['user_id']}, "
                f"NULL, {sql_escape(comment['content'])}, "
                f"{comment['like_count']}, {sql_escape(comment['created_at'].strftime('%Y-%m-%d %H:%M:%S'))})"
            )

        for user_id, post_id in post_likes:
            self.line(f"INSERT INTO likes (user_id, target_type, target_id) VALUES ({user_id}, 1, {post_id})")
        for user_id, comment_id in comment_likes:
            self.line(f"INSERT INTO likes (user_id, target_type, target_id) VALUES ({user_id}, 2, {comment_id})")
        for user_id, post_id in collections:
            self.line(f"INSERT INTO collections (user_id, post_id) VALUES ({user_id}, {post_id})")

        OUTPUT_FILE.write_text("".join(self.sql), encoding="utf-8")
        QUALITY_REPORT_FILE.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"已生成 {OUTPUT_FILE}")
        print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    SeedGenerator().generate()
