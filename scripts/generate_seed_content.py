#!/usr/bin/env python3
"""使用 OpenAI 兼容接口生成 500 篇非模板化旅游社区笔记。"""

from __future__ import annotations

import argparse
import csv
import difflib
import hashlib
import json
import os
import subprocess
import tempfile
import re
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any
from urllib.parse import urljoin


BASE_DIR = Path(__file__).resolve().parent
DESTINATIONS_FILE = BASE_DIR / "seed_data" / "destinations.csv"
LOCAL_ENV_FILE = BASE_DIR / "seed_data" / ".env.local"
OUTPUT_FILE = BASE_DIR / "seed_data" / "generated_posts.json"
REPORT_FILE = BASE_DIR / "seed_data" / "llm_generation_report.json"
CACHE_DIR = BASE_DIR / "seed_data" / ".seed_llm_cache"

PROMPT_VERSION = 7
POSTS_PER_DESTINATION = 10
BATCH_SIZE = 5
MODEL = "deepseek-v4-pro"
BASE_URL = "https://api.deepseek.com/"
DEFAULT_MAX_REQUESTS = 125
INPUT_PRICE = 3.0
CACHE_HIT_INPUT_PRICE = 0.025
OUTPUT_PRICE = 6.0
FORBIDDEN_MARKERS = ("演示笔记", "模拟笔记", "演示数据", "模拟数据", "Pexels API", "作为AI")

CONTENT_ANGLES = [
    "完整行程规划：强调节奏、区域组合和机动时间",
    "摄影与观察：讨论场景、光线、构图和拍摄边界",
    "当地饮食：围绕地方食物、用餐节奏和选择方法",
    "交通组织：减少折返，解释不同交通方式的取舍",
    "季节选择：讨论天气、客流、自然景观和备选方案",
    "住宿选择：从区域、行程和抵达时间分析，不推荐具体商家",
    "预算规划：拆解费用类别，不编造实时价格",
    "第一次到访：给新手一条有重点但不过载的路线",
    "避坑与安全：指出常见误区、信息时效和行为边界",
    "慢旅行：以半日空白、步行观察和在地体验为重点",
]

SYSTEM_PROMPT = """你是一名资深中文旅游社区编辑。请根据给出的结构化事实创作原创旅游笔记。

写作要求：
1. 标题、正文、标签和评论必须完全由你创作，不套用固定开头、固定结尾或重复段落结构。
2. 每篇正文写 500—1500 个中文字符，根据主题和写作角度自然决定具体篇幅；同一批应包含不同长度的文章，避免篇幅趋同。语言自然、有个人观察感，适合旅游社区，而不是百科、营销软文或行程表。
3. 同一批文章的叙述方式、段落数量、开头和结尾必须明显不同。
4. 只能使用提供的事实；不要虚构商家、酒店、票价、营业时间、道路开放状态或亲身经历。
5. 可以提出规划建议，但变化较快的信息应提醒出发前通过官方渠道核对。
6. 不得出现“演示、模拟、种子数据、测试数据、Pexels、AI生成”等元叙事。
7. 不要输出 Markdown 标题、表格、代码块、引用或图片来源说明。
8. 每篇生成 4—8 条自然、具体且角度不同的读者评论，必须回应对应正文中的具体信息，每条 8—80 个字符。
9. 返回严格 JSON 对象，不要附加解释。"""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="用大模型生成高质量旅游社区笔记")
    parser.add_argument("--estimate-only", action="store_true", help="只输出成本估算，不调用模型")
    parser.add_argument("--max-requests", type=int, default=DEFAULT_MAX_REQUESTS, help="本次最多真实请求数")
    parser.add_argument("--limit-destinations", type=int, help="仅生成前 N 个目的地，用于小规模试跑")
    return parser.parse_args()


def load_env_file(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip("'\"")
    return values


def configuration() -> dict[str, Any]:
    local_values = load_env_file(LOCAL_ENV_FILE)
    return {
        "api_key": os.getenv("SEED_LLM_API_KEY") or local_values.get("SEED_LLM_API_KEY", ""),
        "base_url": BASE_URL,
        "model": MODEL,
        "input_price": INPUT_PRICE,
        "output_price": OUTPUT_PRICE,
        "cache_hit_input_price": CACHE_HIT_INPUT_PRICE,
    }


def load_destinations(limit: int | None = None) -> list[dict[str, str]]:
    with DESTINATIONS_FILE.open(encoding="utf-8", newline="") as file:
        destinations = list(csv.DictReader(file, delimiter="|"))
    return destinations[:limit] if limit else destinations


def build_user_prompt(destination: dict[str, str], angle_indexes: list[int]) -> str:
    requested = [
        {"angle_index": index, "writing_goal": CONTENT_ANGLES[index]}
        for index in angle_indexes
    ]
    facts = {
        "destination_key": destination["key"],
        "category": destination["category"],
        "name": destination["name"],
        "region": destination["region"],
        "recommended_season": destination["season"],
        "suggested_days": int(destination["days"]),
        "highlights": destination["highlights"].split(";"),
        "foods": destination["foods"].split(";"),
        "transport_fact": destination["transport"],
    }
    schema = {
        "posts": [
            {
                "angle_index": "必须等于请求中的 angle_index",
                "title": "12—32个中文字符",
                "content": "500—1500个中文字符、长度与写作目标匹配的纯文本，可用换行分段",
                "tags": ["3—5个简短标签，不强制包含目的地名称"],
                "comments": ["4—8条自然读者评论，每条8—80个字符"],
            }
        ]
    }
    return (
        "目的地事实：\n"
        + json.dumps(facts, ensure_ascii=False, indent=2)
        + "\n\n本批写作任务：\n"
        + json.dumps(requested, ensure_ascii=False, indent=2)
        + "\n\n返回结构：\n"
        + json.dumps(schema, ensure_ascii=False, indent=2)
        + f"\n\nposts 必须恰好包含 {len(angle_indexes)} 项，并按 angle_index 升序排列。"
    )


def cache_path(destination_key: str, angle_indexes: list[int], model: str, prompt: str) -> Path:
    digest = hashlib.sha256(
        f"{PROMPT_VERSION}|{model}|{destination_key}|{angle_indexes}|{prompt}".encode()
    ).hexdigest()[:20]
    return CACHE_DIR / f"{destination_key}-{angle_indexes[0]}-{angle_indexes[-1]}-{digest}.json"


def extract_json(content: str) -> dict[str, Any]:
    stripped = content.strip()
    if stripped.startswith("```"):
        stripped = stripped.removeprefix("```json").removeprefix("```").strip()
        stripped = stripped.removesuffix("```").strip()
    return json.loads(stripped)


def validate_post(
    post: dict[str, Any],
    destination: dict[str, str],
    expected_angle: int,
) -> dict[str, Any]:
    if not isinstance(post, dict):
        raise ValueError(f"缺少 angle={expected_angle} 的文章")
    try:
        actual_angle = int(post.get("angle_index", -1))
    except (TypeError, ValueError) as error:
        raise ValueError(f"angle_index 应为 {expected_angle}") from error
    if actual_angle != expected_angle:
        raise ValueError(f"angle_index 应为 {expected_angle}")
    title = str(post.get("title") or "").strip()
    content = str(post.get("content") or "").strip()
    tags = list(dict.fromkeys(
        str(tag).strip() for tag in post.get("tags") or [] if str(tag).strip()
    ))[:5]
    comments = list(dict.fromkeys(
        str(comment).strip()
        for comment in post.get("comments") or []
        if str(comment).strip()
    ))
    if not 12 <= len(title) <= 40:
        raise ValueError(f"标题长度为{len(title)}，必须为12—40个字符")
    if not content:
        raise ValueError("正文不能为空")
    if any(marker in title or marker in content for marker in FORBIDDEN_MARKERS):
        raise ValueError("标题或正文含元叙事标记")
    if not 3 <= len(tags) <= 5:
        raise ValueError(f"标签数量为{len(tags)}，必须为3—5个")
    if not 4 <= len(comments) <= 8:
        raise ValueError(f"评论数量为{len(comments)}，必须为4—8条")
    if any(marker in comment for comment in comments for marker in FORBIDDEN_MARKERS):
        raise ValueError("评论含元叙事标记")
    return {
        "destination_key": destination["key"],
        "angle_index": expected_angle,
        "title": title,
        "content": content,
        "tags": tags,
        "comments": comments,
    }


def validate_batch(
    response: dict[str, Any],
    destination: dict[str, str],
    angle_indexes: list[int],
) -> list[dict[str, Any]]:
    posts = response.get("posts")
    if not isinstance(posts, list) or len(posts) != len(angle_indexes):
        raise ValueError(f"posts 数量必须为 {len(angle_indexes)}")
    return [
        validate_post(post, destination, expected_angle)
        for expected_angle, post in zip(angle_indexes, posts, strict=True)
    ]


class ModelClient:
    def __init__(self, config: dict[str, Any], max_requests: int):
        self.config = config
        self.max_requests = max_requests
        self.request_count = 0
        self.input_tokens = 0
        self.cache_hit_input_tokens = 0
        self.output_tokens = 0

    def generate(self, prompt: str) -> dict[str, Any]:
        if self.request_count >= self.max_requests:
            raise RuntimeError(f"已达到本次请求上限 {self.max_requests}")
        body = {
            "model": self.config["model"],
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": prompt},
            ],
            "temperature": 0.85,
            "top_p": 0.9,
            "max_tokens": 6500,
            "response_format": {"type": "json_object"},
        }
        body["thinking"] = {"type": "disabled"}
        endpoint = urljoin(self.config["base_url"], "chat/completions")
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", suffix=".json") as body_file:
            json.dump(body, body_file, ensure_ascii=False)
            body_file.flush()
            command = [
                "curl", "-4", "--silent", "--show-error", "--fail-with-body",
                "--connect-timeout", "10", "--max-time", "180",
                "-H", f"Authorization: Bearer {self.config['api_key']}",
                "-H", "Content-Type: application/json",
                "--data-binary", f"@{body_file.name}",
                endpoint,
            ]
            completed = subprocess.run(command, check=True, capture_output=True, text=True)
        self.request_count += 1
        payload = json.loads(completed.stdout)
        usage = payload.get("usage") or {}
        self.input_tokens += int(usage.get("prompt_tokens") or 0)
        self.cache_hit_input_tokens += int(usage.get("prompt_cache_hit_tokens") or 0)
        self.output_tokens += int(usage.get("completion_tokens") or 0)
        content = payload["choices"][0]["message"]["content"]
        return extract_json(content)

    def cost_cny(self) -> float:
        cache_miss_input_tokens = max(0, self.input_tokens - self.cache_hit_input_tokens)
        return (
            cache_miss_input_tokens / 1_000_000 * self.config["input_price"]
            + self.cache_hit_input_tokens / 1_000_000 * self.config["cache_hit_input_price"]
            + self.output_tokens / 1_000_000 * self.config["output_price"]
        )


def build_repair_prompt(
    destination: dict[str, str],
    angle_index: int,
    previous_post: Any,
    validation_error: str,
) -> str:
    """将具体校验错误反馈给模型，只重写单篇不合格内容。"""

    return (
        build_user_prompt(destination, [angle_index])
        + "\n\n上一次生成结果未通过校验："
        + validation_error
        + "\n请只修复这一篇，完整返回包含一个元素的 posts JSON。"
        + "\n上一次结果：\n"
        + json.dumps(previous_post, ensure_ascii=False)
    )


def repair_post(
    client: ModelClient,
    destination: dict[str, str],
    angle_index: int,
    previous_post: Any,
    validation_error: str,
) -> dict[str, Any]:
    """最多修复两次；每次都把最新失败原因和结果反馈给模型。"""

    last_error = validation_error
    candidate = previous_post
    for attempt in range(1, 3):
        prompt = build_repair_prompt(destination, angle_index, candidate, last_error)
        try:
            response = client.generate(prompt)
            posts = response.get("posts") if isinstance(response, dict) else None
            if not isinstance(posts, list) or len(posts) != 1:
                raise ValueError("修复结果的posts数量必须为1")
            candidate = posts[0]
            repaired = validate_post(candidate, destination, angle_index)
            print(
                f"  修复 {destination['name']} angle={angle_index}："
                f"第{attempt}次成功"
            )
            return repaired
        except (KeyError, TypeError, ValueError, json.JSONDecodeError) as error:
            last_error = str(error)
    raise RuntimeError(
        f"{destination['name']} angle={angle_index} 修复失败：{last_error}"
    )


def generate_batch(
    client: ModelClient,
    destination: dict[str, str],
    angle_indexes: list[int],
) -> list[dict[str, Any]]:
    """生成一个批次，并仅修复缺失或不合格的单篇内容。"""

    prompt = build_user_prompt(destination, angle_indexes)
    try:
        response = client.generate(prompt)
    except json.JSONDecodeError:
        response = client.generate(prompt + "\n\n上一次响应不是合法JSON，请严格按返回结构重新生成。")

    raw_posts = response.get("posts") if isinstance(response, dict) else None
    candidates: dict[int, dict[str, Any]] = {}
    if isinstance(raw_posts, list):
        for post in raw_posts:
            if not isinstance(post, dict):
                continue
            try:
                angle_index = int(post.get("angle_index"))
            except (TypeError, ValueError):
                continue
            if angle_index in angle_indexes and angle_index not in candidates:
                candidates[angle_index] = post

    completed: list[dict[str, Any]] = []
    for angle_index in angle_indexes:
        candidate = candidates.get(angle_index)
        try:
            completed.append(validate_post(candidate, destination, angle_index))
        except (TypeError, ValueError) as error:
            print(f"  待修复 {destination['name']} angle={angle_index}：{error}")
            completed.append(
                repair_post(client, destination, angle_index, candidate, str(error))
            )
    return completed


def validate_corpus(posts: list[dict[str, Any]]) -> None:
    """检查模型是否在不同请求中退化成重复句式，而不只检查全文完全相同。"""

    sentence_counts: Counter[str] = Counter()
    by_destination: defaultdict[str, list[str]] = defaultdict(list)
    for post in posts:
        content = re.sub(r"\s+", "", post["content"])
        by_destination[post["destination_key"]].append(content)
        for sentence in re.split(r"[。！？\n]+", content):
            if len(sentence) >= 24:
                sentence_counts[sentence] += 1

    repeated_sentences = [sentence for sentence, count in sentence_counts.items() if count > 2]
    if repeated_sentences:
        raise RuntimeError(f"模型跨文章重复了长句：{repeated_sentences[:3]}")

    for destination_key, contents in by_destination.items():
        for left_index, left in enumerate(contents):
            for right in contents[left_index + 1 :]:
                similarity = difflib.SequenceMatcher(None, left, right, autojunk=False).ratio()
                if similarity >= 0.72:
                    raise RuntimeError(
                        f"{destination_key} 内两篇正文相似度达到 {similarity:.1%}，疑似模板化"
                    )

    comments = [comment for post in posts for comment in post["comments"]]
    if len(comments) != len(set(comments)):
        raise RuntimeError("模型生成结果存在完全重复评论")


def estimate(config: dict[str, Any], destination_count: int) -> dict[str, Any]:
    post_count = destination_count * POSTS_PER_DESTINATION
    # 长文不设上限，预算按每篇正文和评论合计约1400个输出Token估算。
    input_tokens = post_count * 850
    output_tokens = post_count * 1400
    base_cost = (
        input_tokens / 1_000_000 * config["input_price"]
        + output_tokens / 1_000_000 * config["output_price"]
    )
    return {
        "model": config["model"],
        "posts": post_count,
        "planned_requests": destination_count * 2,
        "estimated_input_tokens": input_tokens,
        "estimated_output_tokens": output_tokens,
        "estimated_cost_cny": round(base_cost + 1e-9, 2),
        "estimated_cost_with_20_percent_retry_cny": round(base_cost * 1.2 + 1e-9, 2),
        "input_price_cny_per_million": config["input_price"],
        "output_price_cny_per_million": config["output_price"],
    }


def main() -> None:
    args = parse_args()
    config = configuration()
    destinations = load_destinations(args.limit_destinations)
    cost_estimate = estimate(config, len(destinations))
    if args.estimate_only:
        print(json.dumps(cost_estimate, ensure_ascii=False, indent=2))
        return
    if not config["api_key"]:
        raise RuntimeError("缺少 SEED_LLM_API_KEY")

    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    client = ModelClient(config, args.max_requests)
    all_posts: list[dict[str, Any]] = []
    generated_batches = 0
    for destination_index, destination in enumerate(destinations, start=1):
        for batch_start in range(0, POSTS_PER_DESTINATION, BATCH_SIZE):
            angle_indexes = list(range(batch_start, batch_start + BATCH_SIZE))
            prompt = build_user_prompt(destination, angle_indexes)
            cache_file = cache_path(destination["key"], angle_indexes, config["model"], prompt)
            if cache_file.exists():
                posts = validate_batch(
                    json.loads(cache_file.read_text(encoding="utf-8")),
                    destination,
                    angle_indexes,
                )
            else:
                generated_batches += 1
                posts = generate_batch(client, destination, angle_indexes)
                cache_file.write_text(
                    json.dumps({"posts": posts}, ensure_ascii=False, indent=2) + "\n",
                    encoding="utf-8",
                )
            all_posts.extend(posts)
        print(f"[{destination_index:02d}/{len(destinations)}] {destination['name']}：10 篇")

    expected_count = len(destinations) * POSTS_PER_DESTINATION
    if len(all_posts) != expected_count:
        raise RuntimeError(f"应生成 {expected_count} 篇，实际为 {len(all_posts)}")
    if len({post["title"] for post in all_posts}) != len(all_posts):
        raise RuntimeError("模型生成结果存在完全重复标题")
    if len({post["content"] for post in all_posts}) != len(all_posts):
        raise RuntimeError("模型生成结果存在完全重复正文")
    validate_corpus(all_posts)

    output = {
        "schema_version": 1,
        "prompt_version": PROMPT_VERSION,
        "model": config["model"],
        "posts": all_posts,
    }
    OUTPUT_FILE.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    report = {
        **cost_estimate,
        "actual_requests": client.request_count,
        "actual_input_tokens": client.input_tokens,
        "actual_cache_hit_input_tokens": client.cache_hit_input_tokens,
        "actual_output_tokens": client.output_tokens,
        "actual_cost_cny": round(client.cost_cny(), 4),
        "generated_batches": generated_batches,
        "extra_requests": max(0, client.request_count - generated_batches),
        "cached_batches": len(destinations) * 2 - generated_batches,
    }
    REPORT_FILE.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
