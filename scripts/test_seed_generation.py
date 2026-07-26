#!/usr/bin/env python3
"""不消耗 Pexels 配额的种子生成器回归测试。"""

from __future__ import annotations

import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

import generate_sql
import generate_seed_content
import refresh_seed_avatar_media
import validate_seed_data


class SeedGeneratorTest(unittest.TestCase):

    def test_avatar_selection_filters_people_and_balances_pet_queries(self) -> None:
        def asset(asset_id: int, alt_text: str, query: str) -> dict:
            return {
                "provider": "pexels",
                "provider_asset_id": str(asset_id),
                "source_url": f"https://www.pexels.com/photo/pet-portrait-{asset_id}/",
                "alt_text": alt_text,
                "query": query,
            }

        groups = [
            [
                asset(1, "A woman holding a dog", "dog portrait"),
                asset(2, "Golden retriever portrait", "dog portrait"),
            ],
            [
                asset(3, "Tabby cat looking at camera", "cat portrait"),
                asset(4, "Black cat portrait", "cat portrait"),
            ],
            [
                asset(5, "White rabbit portrait", "rabbit portrait"),
                asset(6, "Brown rabbit on grass", "rabbit portrait"),
            ],
        ]

        avatars, report = refresh_seed_avatar_media.select_avatars(
            groups,
            excluded_ids={"4"},
            expected=3,
        )

        self.assertEqual(["2", "3", "5"], [avatar["provider_asset_id"] for avatar in avatars])
        self.assertEqual(1, report["rejected_people"])
        self.assertEqual(1, report["rejected_ids"])

    def test_avatar_person_filter_checks_alt_text_and_source_slug(self) -> None:
        self.assertTrue(refresh_seed_avatar_media.contains_person({
            "alt_text": "Cute dog outdoors",
            "source_url": "https://www.pexels.com/photo/person-holding-dog-10/",
        }))
        self.assertFalse(refresh_seed_avatar_media.contains_person({
            "alt_text": "Close-up portrait of a playful puppy",
            "source_url": "https://www.pexels.com/photo/playful-puppy-11/",
        }))

    def test_only_repairs_the_invalid_post(self) -> None:
        destination = generate_seed_content.load_destinations(1)[0]

        def post(angle_index: int, content_length: int) -> dict:
            return {
                "angle_index": angle_index,
                "title": f"桂林阳朔路线规划原创标题{angle_index}",
                "content": "旅" * content_length,
                "tags": ["路线规划", "山水旅行", "公共交通"],
                "comments": [
                    f"这是角度{angle_index}的第一条具体评论",
                    f"这是角度{angle_index}的第二条具体评论",
                    f"这是角度{angle_index}的第三条具体评论",
                    f"这是角度{angle_index}的第四条具体评论",
                ],
            }

        class FakeClient:
            def __init__(self) -> None:
                self.prompts: list[str] = []
                self.responses = [
                    {
                        "posts": [
                            {**post(0, 475), "tags": ["路线规划", "山水旅行"]},
                            *[post(index, 600) for index in range(1, 5)],
                        ]
                    },
                    {"posts": [post(0, 650)]},
                ]

            def generate(self, prompt: str) -> dict:
                self.prompts.append(prompt)
                return self.responses.pop(0)

        client = FakeClient()
        posts = generate_seed_content.generate_batch(client, destination, list(range(5)))

        self.assertEqual(2, len(client.prompts))
        self.assertIn("标签数量为2", client.prompts[1])
        self.assertEqual(650, len(posts[0]["content"]))
        self.assertEqual(600, len(posts[1]["content"]))

    def test_accepts_short_and_long_post_content(self) -> None:
        destination = generate_seed_content.load_destinations(1)[0]
        base_post = {
            "angle_index": 0,
            "title": "桂林阳朔长篇旅行规划完整指南",
            "tags": ["路线规划", "山水旅行", "公共交通"],
            "comments": [
                "短",
                "长" * 100,
                "长篇内容很适合进一步检索细节",
                "景点之间的节奏安排值得参考",
            ],
        }

        short_result = generate_seed_content.validate_post(
            {**base_post, "content": "简短但完整的旅行观察。"},
            destination,
            0,
        )
        long_result = generate_seed_content.validate_post(
            {**base_post, "content": "长" * 5000},
            destination,
            0,
        )

        self.assertLess(len(short_result["content"]), 500)
        self.assertEqual(5000, len(long_result["content"]))

    def test_generates_500_reproducible_pexels_posts(self) -> None:
        with generate_sql.DESTINATIONS_FILE.open(encoding="utf-8") as file:
            destination_keys = [line.split("|", 1)[0] for line in file.read().splitlines()[1:] if line]

        def asset(asset_id: int) -> dict:
            return {
                "provider": "pexels",
                "provider_asset_id": str(asset_id),
                "image_url": f"https://images.pexels.com/photos/{asset_id}/pexels-photo-{asset_id}.jpeg",
                "thumbnail_url": f"https://images.pexels.com/photos/{asset_id}/pexels-photo-{asset_id}.jpeg?fit=crop&w=350",
                "source_url": f"https://www.pexels.com/photo/{asset_id}/",
                "photographer": f"Photographer {asset_id}",
                "photographer_url": f"https://www.pexels.com/@photographer-{asset_id}/",
                "license_name": "Pexels License",
                "license_url": "https://www.pexels.com/license/",
                "alt_text": f"Travel image {asset_id}",
                "width": 2400,
                "height": 1600,
                "query": "test",
            }

        post_manifest = {
            "schema_version": 1,
            "provider": "pexels",
            "destinations": {
                key: [asset(1000 + destination_index * 40 + offset) for offset in range(40)]
                for destination_index, key in enumerate(destination_keys)
            },
        }
        avatar_manifest = {
            "schema_version": 1,
            "provider": "pexels",
            "avatars": [asset(index) for index in range(1, 51)],
        }
        generated_posts = {
            "schema_version": 1,
            "model": "test-model",
            "posts": [
                {
                    "destination_key": key,
                    "angle_index": angle_index,
                    "title": f"{key} 的原创旅行标题 {angle_index}",
                    "content": f"{key} 的原创旅行正文，角度编号为 {angle_index}。"
                    f"这段测试内容用于验证 SQL 生成链路，不会写入仓库正式数据。{destination_index}",
                    "tags": [f"角度{angle_index}", "旅行", "路线规划"],
                    "comments": [
                        f"关于{key}角度{angle_index}的第一条原创评论",
                        f"关于{key}角度{angle_index}的第二条原创评论",
                        f"关于{key}角度{angle_index}的第三条原创评论",
                        f"关于{key}角度{angle_index}的第四条原创评论",
                    ],
                }
                for destination_index, key in enumerate(destination_keys)
                for angle_index in range(10)
            ],
        }

        with tempfile.TemporaryDirectory(prefix="xiaolvshu-seed-test-") as temp_dir:
            root = Path(temp_dir)
            post_manifest_file = root / "post_media_manifest.json"
            avatar_manifest_file = root / "avatar_media_manifest.json"
            generated_posts_file = root / "generated_posts.json"
            output_file = root / "data.sql"
            report_file = root / "quality_report.json"
            post_manifest_file.write_text(json.dumps(post_manifest), encoding="utf-8")
            avatar_manifest_file.write_text(json.dumps(avatar_manifest), encoding="utf-8")
            generated_posts_file.write_text(json.dumps(generated_posts, ensure_ascii=False), encoding="utf-8")

            original_paths = (
                generate_sql.POST_MEDIA_MANIFEST_FILE,
                generate_sql.AVATAR_MEDIA_MANIFEST_FILE,
                generate_sql.GENERATED_POSTS_FILE,
                generate_sql.OUTPUT_FILE,
                generate_sql.QUALITY_REPORT_FILE,
            )
            generate_sql.POST_MEDIA_MANIFEST_FILE = post_manifest_file
            generate_sql.AVATAR_MEDIA_MANIFEST_FILE = avatar_manifest_file
            generate_sql.GENERATED_POSTS_FILE = generated_posts_file
            generate_sql.OUTPUT_FILE = output_file
            generate_sql.QUALITY_REPORT_FILE = report_file
            try:
                with contextlib.redirect_stdout(io.StringIO()):
                    generate_sql.SeedGenerator().generate()
            finally:
                (
                    generate_sql.POST_MEDIA_MANIFEST_FILE,
                    generate_sql.AVATAR_MEDIA_MANIFEST_FILE,
                    generate_sql.GENERATED_POSTS_FILE,
                    generate_sql.OUTPUT_FILE,
                    generate_sql.QUALITY_REPORT_FILE,
                ) = original_paths

            report = json.loads(report_file.read_text(encoding="utf-8"))
            sql = output_file.read_text(encoding="utf-8")
            self.assertEqual(500, report["posts"])
            self.assertEqual(500, report["unique_titles"])
            self.assertEqual(500, report["unique_contents"])
            self.assertGreaterEqual(report["unique_post_images"], 1000)
            self.assertLessEqual(report["unique_post_images"], 2000)
            self.assertEqual(2000, report["comments"])
            self.assertEqual(2000, report["unique_comments"])
            self.assertEqual("pexels", report["provider"])
            self.assertEqual(500, sql.count("INSERT INTO posts "))
            self.assertEqual(report["post_images"], sql.count("INSERT INTO post_images "))
            self.assertEqual(2000, sql.count("INSERT INTO comments "))
            self.assertNotIn("已经加入行程单", sql)
            self.assertNotIn("demo-token-", sql)
            self.assertIn("'user001'", sql)
            self.assertIn("'云游四海'", sql)
            self.assertIn("https://images.pexels.com/photos/1/", sql)
            for marker in generate_sql.FORBIDDEN_CONTENT_MARKERS:
                self.assertNotIn(marker, "\n".join(
                    post_line for post_line in sql.splitlines() if post_line.startswith("INSERT INTO posts ")
                ))

            validation_paths = (validate_seed_data.SQL_FILE, validate_seed_data.REPORT_FILE)
            validate_seed_data.SQL_FILE = output_file
            validate_seed_data.REPORT_FILE = report_file
            try:
                with contextlib.redirect_stdout(io.StringIO()):
                    validate_seed_data.main()
            finally:
                validate_seed_data.SQL_FILE, validate_seed_data.REPORT_FILE = validation_paths


if __name__ == "__main__":
    unittest.main()
