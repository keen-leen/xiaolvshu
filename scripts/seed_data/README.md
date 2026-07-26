# 高质量演示数据资料包

`destinations.csv` 是 500 篇公开演示笔记的事实约束源。每个目的地固定生成 10 篇不同主题的笔记，不直接复制第三方旅游社区文案。

`post_media_manifest.json` 由 `../refresh_seed_post_media.py` 生成，只保存笔记图片，每个目的地准备40张唯一素材，以支持每篇随机分配2—4张；`avatar_media_manifest.json` 由 `../refresh_seed_avatar_media.py` 使用 `pets` 关键词和 `square` 方向独立生成，只保存宠物头像。两者都按照 Pexels 官方 API 文档采集，不手工维护，并保存资源 ID、摄影师、摄影师主页、图片原始页面、Pexels License 和替代文本。

`users.json` 固化了旧版 `data.sql` 中原有的50个用户资料。新版生成器保留原来的账号、昵称、简介、地区和扩展资料，只使用独立头像清单替换头像及其Pexels署名；关注数、获赞数和发帖数会按照新关系数据重新计算。

常用命令：

```bash
# 一次完整刷新通常只需 50 次请求；脚本本次运行硬上限为 150 次，
# 本机一小时滚动安全上限为 180 次，均低于官方每小时 200 次。
PEXELS_API_KEY=... python3 scripts/refresh_seed_post_media.py
PEXELS_API_KEY=... python3 scripts/refresh_seed_avatar_media.py

# 生成固定种子的 SQL 并执行质量检查
python3 scripts/generate_seed_content.py
python3 scripts/generate_sql.py
python3 scripts/validate_seed_data.py
```

也可以把 `.env.example` 复制为 `.env.local` 并填入密钥，避免密钥出现在命令历史中；`.env.local` 已被 Git 忽略。

两个图片脚本共享查询缓存和本机请求日志，重复执行不会再次消耗相同查询的 API 配额。只有明确传入 `--refresh-cache` 才会刷新缓存。成功响应中的 `X-Ratelimit-Limit`、`X-Ratelimit-Remaining` 和 `X-Ratelimit-Reset` 会写入各自清单，便于核对每月 20,000 次配额。

`generate_seed_content.py` 固定使用 DeepSeek 官方接口和 `deepseek-v4-pro` 生成标题、正文、3—5个标签和每篇4—8条评论，并显式关闭思考模式。提示词要求正文500—1500个字符、评论8—80个字符，但本地校验不检查两者的长度；标签不强制包含目的地。脚本只提供目的地事实、十种内容目标和 JSON 输出结构，不包含正文或评论模板。每个目的地分两批生成，完整运行计划请求约100次；批次结果缓存在 `.seed_llm_cache/`，中断后可继续。

批次响应会逐篇校验。若只有部分文章长度、标签或评论不合格，脚本保留其他合格文章，只把具体错误和原结果反馈给DeepSeek修复失败项；修复完整批次后才写入缓存。只有响应本身不是合法JSON时才重新请求整个批次。

公开展示时使用 API 返回的 `source_url`、`photographer`、`photographer_url`、`license_name` 和 `license_url` 提供署名，并显示指向 Pexels 的显著链接。
