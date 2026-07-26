# 公开演示数据生成

## 目标与边界

开发数据库使用 50 个目的地资料包生成 500 篇旅游笔记。数据用于界面展示、搜索和 RAG 验证，不包含从其他旅游社区复制的用户文案。

标题、正文、标签和评论由大模型完整生成。脚本只提供目的地事实、内容角度和 JSON 结构，不提供任何正文或评论模板；“演示、模拟、测试数据、AI 生成、Pexels”等元叙事禁止进入生成文本，避免污染全文检索与 RAG 语料。

所有帖子图片和演示头像只通过 [Pexels API](https://www.pexels.com/api/documentation/) 获取。笔记图片和头像使用两个独立脚本、两个独立清单生成；生成结果保留摄影师、摄影师主页、图片详情页和 [Pexels License](https://www.pexels.com/license/)；详情页会展示当前图片的摄影师和 Pexels 链接。

用户资料不再根据目的地重新生成。`scripts/seed_data/users.json` 保存原有50个用户的账号、昵称、简介、地区和扩展资料；头像由独立Pexels清单替换，关系统计则按照新生成的帖子、关注和点赞重新计算。

## API 配额

Pexels 默认限制为每小时 200 次、每月 20,000 次请求。当前采集器采用以下保护：

- 每个目的地先发起一次 `per_page=80` 的搜索，通常完整刷新约 50 次请求。
- 单次运行硬上限为 150 次，本机滚动一小时安全上限为 180 次。
- 查询响应缓存在 `scripts/seed_data/.pexels_cache/`，重复运行不重复请求。
- 只有显式传入 `--refresh-cache` 才忽略缓存。
- 记录成功响应中的月配额 `X-Ratelimit-*` 头；剩余量低于 100 时主动停止。
- 不自动重试 HTTP 429，也不通过多密钥、代理或并发请求规避限制。

API 文档说明 `X-Ratelimit-Limit`、`X-Ratelimit-Remaining` 和 `X-Ratelimit-Reset` 对应月配额；小时用量由本地请求日志额外保护。

## 使用方式

密钥不要写入仓库。推荐复制本地模板：

```bash
cp scripts/seed_data/.env.example scripts/seed_data/.env.local
# 编辑 .env.local，填写 PEXELS_API_KEY 和 SEED_LLM_API_KEY
```

然后依次执行：

```bash
python3 scripts/refresh_seed_post_media.py
python3 scripts/refresh_seed_avatar_media.py
python3 scripts/generate_seed_content.py
python3 scripts/generate_sql.py
python3 scripts/validate_seed_data.py
```

`refresh_seed_post_media.py` 生成 `post_media_manifest.json`；`refresh_seed_avatar_media.py` 生成 `avatar_media_manifest.json`；`generate_seed_content.py` 生成包含笔记和评论的 `generated_posts.json`；`generate_sql.py` 只读取这些结果和固定的 `users.json`，使用固定随机种子分配关系并写出 `docker/dev/mysql/init/data.sql` 和 `quality_report.json`，不再生成任何文本内容。

头像脚本会轮流使用狗、猫、兔子和宠物鸟肖像查询，并根据 Pexels 返回的替代文本和详情页地址过滤人物、人手及人宠合照。由于文本过滤不能替代视觉检查，生成清单后仍应检查50张缩略图；发现不合适的图片时，把详情页中的 Pexels 图片ID追加到 `scripts/seed_data/avatar_excluded_ids.txt`，再运行 `python3 scripts/refresh_seed_avatar_media.py`。已有查询缓存时不会消耗新配额，脚本会自动选择下一张候选图并同步完整归属信息，随后只需重新运行 `python3 scripts/generate_sql.py`。

大模型批次按单篇校验。长度、标签或评论不合格时，只把具体错误反馈给DeepSeek修复失败文章，批次内其他合格结果不会重复生成；修复后的整个批次通过校验后才写入本地缓存。

## 大模型成本

内容生成固定采用 `deepseek-v4-pro`，并显式关闭默认思考模式。提示词要求正文为500—1500个中文字符、评论为8—80个字符，但本地质量门禁不检查正文和评论的长度，以免机械裁剪影响内容完整性与多样性。按500篇笔记及每篇平均6条评论、每篇约850输入Token和1400输出Token估算，总量约为42.5万输入Token和70万输出Token；这只是预算基准，实际费用会随正文长度分布变化：

| 模型 | 输入/输出单价（元/百万 Token） | 基础费用 | 含 20% 重试余量 |
| --- | --- | --- | --- |
| `deepseek-v4-pro` | 3 / 6 | 约 ¥5.48 | 约 ¥6.57 |

运行 `python3 scripts/generate_seed_content.py --estimate-only` 可按当前模型和配置重新计算。脚本的实际费用报告会区分 DeepSeek 返回的缓存命中输入 Token；价格会变化，最终以 DeepSeek 官方账单为准。

## 质量门禁

- 恰好 500 篇笔记，十个分类各不少于 40 篇。
- 标题与正文完全重复数为零。
- 正文和评论不检查长度上下限，但禁止展示数据相关元文本。
- 标签由模型生成3—5个，不强制包含目的地或分类名称。
- 每篇随机分配2—4张图片，同一图片只分配一次，全库重复率低于5%。
- 图片提供方必须为 `pexels`，来源、摄影师、许可证和替代文本不能为空。
- 每篇必须有4—8条不重复且回应正文具体信息的大模型评论。
- 生成时间和随机种子固定；相同资料包与媒体清单应得到相同 SQL。

目的地季节、交通和玩法仅用于生成不含实时承诺的原创演示文案。票价、开放时间、天气、道路和预约规则一律提示用户以官方实时信息为准。
