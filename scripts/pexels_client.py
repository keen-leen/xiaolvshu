"""Pexels搜索、缓存和配额保护的公共实现。"""

from __future__ import annotations

import hashlib
import json
import os
import subprocess
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlencode


BASE_DIR = Path(__file__).resolve().parent
CACHE_DIR = BASE_DIR / "seed_data" / ".pexels_cache"
REQUEST_LOG_FILE = CACHE_DIR / "request_log.json"
LOCAL_ENV_FILE = BASE_DIR / "seed_data" / ".env.local"
PEXELS_API = "https://api.pexels.com/v1/search"
SAFE_HOURLY_REQUEST_LIMIT = 180
MAX_RUN_REQUESTS = 150


def load_api_key() -> str:
    environment_key = os.getenv("PEXELS_API_KEY", "").strip()
    if environment_key:
        return environment_key
    if not LOCAL_ENV_FILE.exists():
        return ""
    for raw_line in LOCAL_ENV_FILE.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key.strip() == "PEXELS_API_KEY":
            return value.strip().strip("'\"")
    return ""


def require_api_key() -> str:
    api_key = load_api_key()
    if not api_key:
        raise RuntimeError(
            "缺少PEXELS_API_KEY；请通过环境变量传入，或写入不会提交的"
            " scripts/seed_data/.env.local"
        )
    return api_key


def pexels_assets(payload: dict, query: str) -> list[dict[str, str]]:
    assets: list[dict[str, str]] = []
    for photo in payload.get("photos", []):
        source = photo.get("src") or {}
        image_url = source.get("large2x") or source.get("large")
        if not image_url:
            continue
        assets.append(
            {
                "provider": "pexels",
                "provider_asset_id": str(photo.get("id")),
                "image_url": image_url,
                "thumbnail_url": source.get("medium") or image_url,
                "source_url": photo.get("url") or "",
                "photographer": photo.get("photographer") or "Pexels contributor",
                "photographer_url": photo.get("photographer_url") or "",
                "license_name": "Pexels License",
                "license_url": "https://www.pexels.com/license/",
                "alt_text": photo.get("alt") or query,
                "width": int(photo.get("width") or 0),
                "height": int(photo.get("height") or 0),
                "query": query,
            }
        )
    return assets


class RequestBudget:
    """共同约束单次运行、一小时滚动用量和Pexels月配额。"""

    def __init__(self, run_limit: int):
        if not 1 <= run_limit <= MAX_RUN_REQUESTS:
            raise ValueError(f"--max-requests 必须在1到{MAX_RUN_REQUESTS}之间")
        self.run_limit = run_limit
        self.run_requests = 0
        self.last_rate_headers: dict[str, str] = {}
        CACHE_DIR.mkdir(parents=True, exist_ok=True)
        self.timestamps = self._recent_timestamps()

    def _recent_timestamps(self) -> list[float]:
        if not REQUEST_LOG_FILE.exists():
            return []
        try:
            values = json.loads(REQUEST_LOG_FILE.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError):
            return []
        cutoff = datetime.now(timezone.utc).timestamp() - 3600
        return [float(value) for value in values if float(value) >= cutoff]

    def before_request(self) -> None:
        if self.run_requests >= self.run_limit:
            raise RuntimeError(f"本次运行已达到请求上限{self.run_limit}")
        if len(self.timestamps) >= SAFE_HOURLY_REQUEST_LIMIT:
            raise RuntimeError(f"本机最近一小时已记录{len(self.timestamps)}次Pexels请求")

    def after_request(self, headers: dict[str, str]) -> None:
        self.run_requests += 1
        self.timestamps.append(datetime.now(timezone.utc).timestamp())
        REQUEST_LOG_FILE.write_text(json.dumps(self.timestamps), encoding="utf-8")
        self.last_rate_headers = {
            "monthly_limit": headers.get("x-ratelimit-limit", ""),
            "monthly_remaining": headers.get("x-ratelimit-remaining", ""),
            "monthly_reset": headers.get("x-ratelimit-reset", ""),
        }
        remaining = self.last_rate_headers["monthly_remaining"]
        if remaining.isdigit() and int(remaining) < 100:
            raise RuntimeError(f"Pexels月配额仅剩{remaining}次，已主动停止")


def search(
    query: str,
    api_key: str,
    budget: RequestBudget,
    refresh_cache: bool,
    orientation: str = "landscape",
) -> list[dict[str, str]]:
    cache_key = hashlib.sha256(f"v1|{query}|{orientation}|80".encode()).hexdigest()
    cache_file = CACHE_DIR / f"{cache_key}.json"
    if cache_file.exists() and not refresh_cache:
        return pexels_assets(json.loads(cache_file.read_text(encoding="utf-8")), query)

    budget.before_request()
    params = urlencode({"query": query, "per_page": 80, "orientation": orientation})
    with tempfile.NamedTemporaryFile(prefix="pexels-headers-", suffix=".txt") as header_file:
        completed = subprocess.run(
            [
                "curl", "-4", "--silent", "--show-error", "--fail-with-body",
                "--connect-timeout", "10", "--max-time", "45",
                "--dump-header", header_file.name,
                "-H", f"Authorization: {api_key}",
                f"{PEXELS_API}?{params}",
            ],
            check=True,
            capture_output=True,
            text=True,
        )
        headers = {}
        for line in Path(header_file.name).read_text(encoding="utf-8", errors="ignore").splitlines():
            if ":" in line:
                key, value = line.split(":", 1)
                headers[key.strip().lower()] = value.strip()

    payload = json.loads(completed.stdout)
    budget.after_request(headers)
    cache_file.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    return pexels_assets(payload, query)
