# 数据库设计

## 事实来源

数据库完整结构以 `docker/dev/mysql/init/schema.sql` 为唯一来源，开发初始化数据位于 `docker/dev/mysql/init/data.sql`。文档用于解释结构，不替代 SQL。

数据库使用 MySQL 8、`utf8mb4` 字符集和 InnoDB。当前共 15 张表。

## 表清单

| 表 | 职责 |
| --- | --- |
| `users` | 用户账号、资料、状态和认证信息 |
| `admin` | 后台管理员账号与角色 |
| `user_sessions` | 用户登录会话 |
| `categories` | 帖子分类 |
| `tags` | 标签 |
| `posts` | 帖子主体、统计和搜索同步状态 |
| `post_images` | 帖子图片 |
| `post_videos` | 帖子视频 |
| `post_tags` | 帖子与标签的多对多关系 |
| `comments` | 评论和回复 |
| `likes` | 用户点赞关系 |
| `collections` | 用户收藏关系 |
| `follows` | 用户关注关系 |
| `notifications` | 评论、点赞、关注、收藏等通知 |
| `audit` | 内容或业务审核记录 |

## 主要关系

```text
users 1---n posts
users 1---n comments
users n---n posts       via likes / collections
users n---n users       via follows
posts 1---n post_images / post_videos / comments
posts n---n tags        via post_tags
users 1---n notifications / user_sessions
```

删除和状态控制应通过现有 Service 规则执行，避免直接修改关联表造成计数或通知不一致。

## posts 搜索状态

`posts` 除标题、内容、作者、分类、统计和发布时间外，还维护两套彼此独立的派生索引状态：

| 字段 | 含义 |
| --- | --- |
| `updated_at` | 业务内容最近更新时间 |
| `is_indexed` | 是否已成功同步到全文搜索索引 |
| `indexed_at` | 最近一次全文索引同步成功时间 |
| `is_vectorized` | 是否已完成 RAG 分块和向量化 |
| `vectorized_at` | 最近一次 RAG 向量化成功时间 |

帖子创建或更新后，相应状态会失效；同步服务成功写入 Elasticsearch 后再更新状态。全文索引与 RAG 向量索引失败时不得互相覆盖状态。

## 初始化约定

Compose 仅在 MySQL 数据卷首次创建时执行初始化 SQL。应遵守：

- 结构变更直接合并到 `schema.sql`，保证新用户可以一次初始化到当前版本。
- 开发演示数据写入 `data.sql`，账号密码必须与运行时密码编码方式一致。
- 真实用户数据、生产导出和真实密钥不得写入初始化脚本。
- 已有数据库升级由维护者结合实际数据单独执行，不能假设重新运行初始化脚本会自动迁移。

搜索索引不是数据库备份，详细同步规则见 [SEARCH_RAG.md](SEARCH_RAG.md)。
