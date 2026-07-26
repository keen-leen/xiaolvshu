#!/usr/bin/env python3
"""按照Pexels官方API刷新带授权信息的笔记图片清单。"""

from __future__ import annotations

import argparse
import csv
import json
from datetime import datetime, timezone
from pathlib import Path

from pexels_client import MAX_RUN_REQUESTS, RequestBudget, require_api_key, search


BASE_DIR = Path(__file__).resolve().parent
DESTINATIONS_FILE = BASE_DIR / "seed_data" / "destinations.csv"
OUTPUT_FILE = BASE_DIR / "seed_data" / "post_media_manifest.json"
ASSETS_PER_DESTINATION = 40


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="刷新Pexels笔记图片元数据")
    parser.add_argument(
        "--assets-per-destination",
        type=int,
        default=ASSETS_PER_DESTINATION,
        help="每个目的地保存的图片数；10篇笔记随机分配2—4张时默认需要40张",
    )
    parser.add_argument(
        "--max-requests",
        type=int,
        default=MAX_RUN_REQUESTS,
        help="本次运行最多发起的真实 API 请求数，默认 150，必须小于每小时 200 次的官方上限",
    )
    parser.add_argument(
        "--refresh-cache",
        action="store_true",
        help="忽略已有查询缓存；会消耗新的 API 配额，非必要不要使用",
    )
    return parser.parse_args()


def load_destinations() -> list[dict[str, str]]:
    with DESTINATIONS_FILE.open(encoding="utf-8", newline="") as file:
        rows = list(csv.DictReader(file, delimiter="|"))
    if len(rows) != 50:
        raise ValueError(f"目的地资料包必须恰好包含 50 条，当前为 {len(rows)} 条")
    return rows


def collect_for_destination(
    destination: dict[str, str],
    api_key: str,
    required_count: int,
    globally_used: set[tuple[str, str]],
    budget: RequestBudget,
    refresh_cache: bool,
) -> list[dict[str, str]]:
    selected: list[dict[str, str]] = []
    queries = [item.strip() for item in destination["image_queries"].split(";") if item.strip()]

    for query in queries:
        fetched = search(query, api_key, budget, refresh_cache)
        for asset in fetched:
            identity = (asset["provider"], asset["provider_asset_id"])
            if identity in globally_used:
                continue
            globally_used.add(identity)
            selected.append(asset)
            if len(selected) >= required_count:
                return selected

    raise RuntimeError(
        f"{destination['name']} 只有 {len(selected)} 张合规图片，少于要求的 {required_count} 张；"
        "请补充更精确的 Pexels image_queries"
    )


def main() -> None:
    args = parse_args()
    if not 2 <= args.assets_per_destination <= 50:
        raise ValueError("--assets-per-destination 必须在 2 到 50 之间")
    api_key = require_api_key()
    destinations = load_destinations()
    globally_used: set[tuple[str, str]] = set()
    destination_assets: dict[str, list[dict[str, str]]] = {}
    budget = RequestBudget(args.max_requests)

    for index, destination in enumerate(destinations, start=1):
        destination_assets[destination["key"]] = collect_for_destination(
            destination,
            api_key,
            args.assets_per_destination,
            globally_used,
            budget,
            args.refresh_cache,
        )
        print(
            f"[{index:02d}/{len(destinations)}] {destination['name']}: "
            f"{len(destination_assets[destination['key']])} 张"
        )

    manifest = {
        "schema_version": 1,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "provider": "pexels",
        "assets_per_destination": args.assets_per_destination,
        "api_requests_this_run": budget.run_requests,
        "rate_limit": budget.last_rate_headers,
        "destinations": destination_assets,
    }
    OUTPUT_FILE.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"媒体清单已写入 {OUTPUT_FILE}，共 {len(globally_used)} 张唯一图片")


if __name__ == "__main__":
    main()
