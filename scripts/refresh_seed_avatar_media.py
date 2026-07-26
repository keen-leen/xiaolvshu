#!/usr/bin/env python3
"""独立获取演示用户头像，避免头像资源与笔记图片采集相互耦合。"""

from __future__ import annotations

import argparse
import json
import re
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from pexels_client import RequestBudget, require_api_key, search


BASE_DIR = Path(__file__).resolve().parent
OUTPUT_FILE = BASE_DIR / "seed_data" / "avatar_media_manifest.json"
EXCLUDED_IDS_FILE = BASE_DIR / "seed_data" / "avatar_excluded_ids.txt"
EXPECTED_AVATARS = 50
AVATAR_QUERIES = (
    "dog portrait",
    "cat portrait",
    "rabbit portrait",
    "pet bird portrait",
)

# Pexels 搜索目前没有“排除人物”参数，只能在拿到候选图片后根据英文替代文本和详情页
# slug 做本地过滤。这里使用明确的人物名词和人宠互动动作，宁可少取一张，也不要把人物
# 合照写入头像清单。视觉检查发现的漏网图片再通过 avatar_excluded_ids.txt 永久排除。
HUMAN_MARKER_PATTERN = re.compile(
    r"\b("
    r"woman|women|man|men|person|people|girl|girls|boy|boys|"
    r"child|children|toddler|teenager|adult|human|owner|owners|"
    r"mother|father|parent|parents|couple|family|families|"
    r"lady|gentleman|tourist|model|hand|hands|arm|arms|foot|feet|legs|lap|"
    r"holding|carrying|hugging|embracing|petting|caressing"
    r")\b",
    flags=re.IGNORECASE,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="刷新Pexels演示用户头像元数据")
    parser.add_argument(
        "--max-requests",
        type=int,
        default=10,
        help="本次最多发起的真实API请求数，默认10",
    )
    parser.add_argument(
        "--refresh-cache",
        action="store_true",
        help="忽略头像查询缓存；会消耗新的Pexels API配额",
    )
    return parser.parse_args()


def load_excluded_ids(path: Path = EXCLUDED_IDS_FILE) -> set[str]:
    if not path.exists():
        return set()
    return {
        line
        for raw_line in path.read_text(encoding="utf-8").splitlines()
        if (line := raw_line.strip()) and not line.startswith("#")
    }


def contains_person(asset: dict[str, Any]) -> bool:
    searchable_text = " ".join((
        str(asset.get("alt_text") or ""),
        str(asset.get("source_url") or "").replace("-", " "),
    ))
    return HUMAN_MARKER_PATTERN.search(searchable_text) is not None


def select_avatars(
    assets_by_query: list[list[dict[str, Any]]],
    excluded_ids: set[str],
    expected: int = EXPECTED_AVATARS,
) -> tuple[list[dict[str, Any]], dict[str, int]]:
    """按查询轮流取图，避免50个头像集中为同一种宠物。"""
    filtered_groups: list[list[dict[str, Any]]] = []
    rejected_people = 0
    rejected_ids = 0
    for assets in assets_by_query:
        filtered: list[dict[str, Any]] = []
        for asset in assets:
            asset_id = str(asset.get("provider_asset_id") or "")
            if asset_id in excluded_ids:
                rejected_ids += 1
                continue
            if contains_person(asset):
                rejected_people += 1
                continue
            filtered.append(asset)
        filtered_groups.append(filtered)

    selected: list[dict[str, Any]] = []
    used: set[tuple[str, str]] = set()
    max_group_size = max((len(group) for group in filtered_groups), default=0)
    for asset_index in range(max_group_size):
        for group in filtered_groups:
            if asset_index >= len(group):
                continue
            asset = group[asset_index]
            identity = (str(asset.get("provider")), str(asset.get("provider_asset_id")))
            if identity in used:
                continue
            used.add(identity)
            selected.append(asset)
            if len(selected) >= expected:
                return selected, {
                    "rejected_people": rejected_people,
                    "rejected_ids": rejected_ids,
                }

    return selected, {
        "rejected_people": rejected_people,
        "rejected_ids": rejected_ids,
    }


def main() -> None:
    args = parse_args()
    api_key = require_api_key()
    budget = RequestBudget(args.max_requests)
    excluded_ids = load_excluded_ids()
    assets_by_query = [
        search(
            query,
            api_key,
            budget,
            args.refresh_cache,
            orientation="square",
        )
        for query in AVATAR_QUERIES
    ]
    avatars, filter_report = select_avatars(assets_by_query, excluded_ids)

    if len(avatars) < EXPECTED_AVATARS:
        raise RuntimeError(
            f"Pexels头像过滤人物和排除清单后只剩 {len(avatars)} 张唯一图片，少于要求的50张"
        )

    manifest = {
        "schema_version": 1,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "provider": "pexels",
        "queries": list(AVATAR_QUERIES),
        "api_requests_this_run": budget.run_requests,
        "rate_limit": budget.last_rate_headers,
        "filter_report": {
            **filter_report,
            "excluded_id_count": len(excluded_ids),
        },
        "avatars": avatars,
    }
    OUTPUT_FILE.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(
        f"头像清单已写入 {OUTPUT_FILE}，共 {len(avatars)} 张唯一图片；"
        f"过滤疑似人物图片 {filter_report['rejected_people']} 张，"
        f"命中手工排除清单 {filter_report['rejected_ids']} 张"
    )


if __name__ == "__main__":
    main()
