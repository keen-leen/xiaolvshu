# 小旅书图文社区数据库设计

## 概述

小旅书图文社区项目数据库基于 MySQL 构建。
- **字符集**: `utf8mb4`
- **排序规则**: `utf8mb4_unicode_ci`
- **存储引擎**: `InnoDB`

> **注意**: 本数据库设计主要通过逻辑关系关联表，未设置物理外键约束 (Foreign Key Constraints)。

## 数据表结构详情

以下是项目中所有数据表的详细结构定义。

### 1. 用户表 (`users`)

存储用户的基本信息及统计数据。

| 字段名 | 类型 | 必填 | 说明 | 索引/备注 |
|---|---|---|---|---|
| `id` | bigint(20) | 是 | 用户ID | **主键**, 自增 |
| `user_id` | varchar(50) | 是 | 小旅书号 | **唯一索引** (`user_id`) |
| `password` | varchar(255) | 否 | 密码 | |
| `nickname` | varchar(100) | 是 | 昵称 | |
| `avatar` | varchar(500) | 否 | 头像URL | |
| `bio` | text | 否 | 个人简介 | |
| `location` | varchar(100) | 否 | IP属地 | |
| `follow_count` | int(11) | 否 | 关注数 | 默认: 0 |
| `fans_count` | int(11) | 否 | 粉丝数 | 默认: 0 |
| `like_count` | int(11) | 否 | 获赞数 | 默认: 0 |
| `post_count` | int | 否 | 发布笔记数 | 默认: 0 |
| `is_active` | tinyint(1) | 否 | 是否激活 | 默认: 1 |
| `last_login_at` | timestamp | 否 | 最后登录时间 | |
| `created_at` | timestamp | 是 | 创建时间 | 默认: CURRENT_TIMESTAMP, 索引 (`idx_created_at`) |
| `updated_at` | timestamp | 是 | 更新时间 | 默认: ON UPDATE CURRENT_TIMESTAMP |
| `gender` | varchar(10) | 否 | 性别 | |
| `zodiac_sign` | varchar(20) | 否 | 星座 | |
| `mbti` | varchar(4) | 否 | MBTI人格类型 | |
| `education` | varchar(50) | 否 | 学历 | |
| `major` | varchar(100) | 否 | 专业 | |
| `interests` | json | 否 | 兴趣爱好 | JSON数组 |
| `verified` | tinyint(1) | 否 | 认证状态 | 0-未认证, 1-已认证, 默认: 0 |

---

### 2. 管理员表 (`admin`)

存储后台管理人员账户。

| 字段名 | 类型 | 必填 | 说明 | 索引/备注 |
|---|---|---|---|---|
| `id` | bigint(20) | 是 | 管理员ID | **主键**, 自增 |
| `username` | varchar(50) | 是 | 管理员用户名 | **唯一索引** (`username`) |
| `password` | varchar(255) | 是 | 管理员密码 | |
| `created_at` | timestamp | 是 | 创建时间 | 默认: CURRENT_TIMESTAMP |

---

### 3. 分类表 (`categories`)

笔记的内容分类。

| 字段名 | 类型 | 必填 | 说明 | 索引/备注 |
|---|---|---|---|---|
| `id` | INT | 是 | 分类ID | **主键**, 自增 |
| `name` | VARCHAR(50) | 是 | 分类名称 | **唯一** |
| `category_title` | VARCHAR(50) | 否 | 分类英文标题 | **唯一索引** (`uk_category_title`), 用于URL |
| `post_count` | BIGINT | 否 | 笔记数量 | 默认: 0 |
| `created_at` | TIMESTAMP | 否 | 创建时间 | 默认: CURRENT_TIMESTAMP |

---

### 4. 笔记表 (`posts`)

核心内容表，存储笔记的主体信息。

| 字段名 | 类型 | 必填 | 说明 | 索引/备注 |
|---|---|---|---|---|
| `id` | bigint(20) | 是 | 笔记ID | **主键**, 自增 |
| `user_id` | bigint(20) | 是 | 发布用户ID | 索引 (`idx_user_id`) |
| `title` | varchar(200) | 是 | 标题 | |
| `content` | text | 是 | 内容 | |
| `category_id` | int(11) | 否 | 分类ID | 索引 (`idx_category_id`) |
| `type` | int(11) | 否 | 笔记类型 | 1-图片, 2-视频, 默认: 1 |
| `view_count` | bigint(20) | 否 | 浏览量 | 默认: 0 |
| `like_count` | int(11) | 否 | 点赞数 | 默认: 0, 索引 (`idx_like_count`) |
| `collect_count` | int(11) | 否 | 收藏数 | 默认: 0 |
| `comment_count` | int(11) | 否 | 评论数 | 默认: 0 |
| `is_draft` | tinyint(1) | 否 | 是否为草稿 | 1-草稿, 0-发布, 默认: 1 |
| `created_at` | timestamp | 是 | 发布时间 | 默认: CURRENT_TIMESTAMP, 索引 (`idx_created_at`) |

---

### 5. 笔记图片表 (`post_images`)

存储图片笔记关联的图片链接。

| 字段名 | 类型 | 必填 | 说明 | 索引/备注 |
|---|---|---|---|---|
| `id` | bigint(20) | 是 | 图片ID | **主键**, 自增 |
| `post_id` | bigint(20) | 是 | 笔记ID | 索引 (`idx_post_id`) |
| `image_url` | varchar(500) | 是 | 图片URL | |

---

### 6. 笔记视频表 (`post_videos`)

存储视频笔记关联的视频及封面链接。

| 字段名 | 类型 | 必填 | 说明 | 索引/备注 |
|---|---|---|---|---|
| `id` | bigint(20) | 是 | 视频ID | **主键**, 自增 |
| `post_id` | bigint(20) | 是 | 笔记ID | 索引 (`idx_post_id`) |
| `cover_url` | varchar(500) | 否 | 视频封面URL | |
| `video_url` | varchar(500) | 是 | 视频URL | |

---

### 7. 标签表 (`tags`)

存储系统中的话题/标签。

| 字段名 | 类型 | 必填 | 说明 | 索引/备注 |
|---|---|---|---|---|
| `id` | int(11) | 是 | 标签ID | **主键**, 自增 |
| `name` | varchar(50) | 是 | 标签名 | **唯一索引** (`name`) |
| `use_count` | int(11) | 否 | 使用次数 | 默认: 0, 索引 (`idx_use_count`) |
| `created_at` | timestamp | 是 | 创建时间 | 默认: CURRENT_TIMESTAMP |

---

### 8. 笔记标签关联表 (`post_tags`)

笔记与标签的多对多关联。

| 字段名 | 类型 | 必填 | 说明 | 索引/备注 |
|---|---|---|---|---|
| `id` | bigint(20) | 是 | 关联ID | **主键**, 自增 |
| `post_id` | bigint(20) | 是 | 笔记ID | 索引 (`idx_post_id`) |
| `tag_id` | int(11) | 是 | 标签ID | 索引 (`idx_tag_id`) |
| `created_at` | timestamp | 是 | 创建时间 | 默认: CURRENT_TIMESTAMP |

> **唯一约束**: `uk_post_tag` (`post_id`, `tag_id`)

---

### 9. 关注关系表 (`follows`)

用户之间的关注关系。

| 字段名 | 类型 | 必填 | 说明 | 索引/备注 |
|---|---|---|---|---|
| `id` | bigint(20) | 是 | 关注ID | **主键**, 自增 |
| `follower_id` | bigint(20) | 是 | 关注者ID | 索引 (`idx_follower_id`) |
| `following_id` | bigint(20) | 是 | 被关注者ID | 索引 (`idx_following_id`) |
| `created_at` | timestamp | 是 | 关注时间 | 默认: CURRENT_TIMESTAMP |

> **唯一约束**: `uk_follow` (`follower_id`, `following_id`)

---

### 10. 点赞表 (`likes`)

用户对笔记或评论的点赞记录。

| 字段名 | 类型 | 必填 | 说明 | 索引/备注 |
|---|---|---|---|---|
| `id` | bigint(20) | 是 | 点赞ID | **主键**, 自增 |
| `user_id` | bigint(20) | 是 | 用户ID | 索引 (`idx_user_id`) |
| `target_type` | tinyint(4) | 是 | 目标类型 | 1-笔记, 2-评论 |
| `target_id` | bigint(20) | 是 | 目标ID | |
| `created_at` | timestamp | 是 | 点赞时间 | 默认: CURRENT_TIMESTAMP |

> **唯一约束**: `uk_user_target` (`user_id`, `target_type`, `target_id`)
> **复合索引**: `idx_target` (`target_type`, `target_id`)

---

### 11. 收藏表 (`collections`)

用户对笔记的收藏记录。

| 字段名 | 类型 | 必填 | 说明 | 索引/备注 |
|---|---|---|---|---|
| `id` | bigint(20) | 是 | 收藏ID | **主键**, 自增 |
| `user_id` | bigint(20) | 是 | 用户ID | 索引 (`idx_user_id`) |
| `post_id` | bigint(20) | 是 | 笔记ID | 索引 (`idx_post_id`) |
| `created_at` | timestamp | 是 | 收藏时间 | 默认: CURRENT_TIMESTAMP |

> **唯一约束**: `uk_user_post` (`user_id`, `post_id`)

---

### 12. 评论表 (`comments`)

笔记下的评论信息。

| 字段名 | 类型 | 必填 | 说明 | 索引/备注 |
|---|---|---|---|---|
| `id` | bigint(20) | 是 | 评论ID | **主键**, 自增 |
| `post_id` | bigint(20) | 是 | 笔记ID | 索引 (`idx_post_id`) |
| `user_id` | bigint(20) | 是 | 评论用户ID | 索引 (`idx_user_id`) |
| `parent_id` | bigint(20) | 否 | 父评论ID | 索引 (`idx_parent_id`), 用于回复 |
| `content` | text | 是 | 评论内容 | |
| `like_count` | int(11) | 否 | 点赞数 | 默认: 0 |
| `created_at` | timestamp | 是 | 评论时间 | 默认: CURRENT_TIMESTAMP |

---

### 13. 通知表 (`notifications`)

用户的消息通知。

| 字段名 | 类型 | 必填 | 说明 | 索引/备注 |
|---|---|---|---|---|
| `id` | bigint(20) | 是 | 通知ID | **主键**, 自增 |
| `user_id` | bigint(20) | 是 | 接收用户ID | 索引 (`idx_user_id`) |
| `sender_id` | bigint(20) | 是 | 发送用户ID | 索引 (`idx_sender_id`) |
| `type` | tinyint(4) | 是 | 通知类型 | 1-点赞, 2-评论, 3-关注 |
| `title` | varchar(200) | 是 | 通知标题 | |
| `target_id` | bigint(20) | 否 | 关联目标ID | 笔记ID等 |
| `comment_id` | bigint(20) | 否 | 关联评论ID | 索引 (`idx_notifications_comment_id`) |
| `is_read` | tinyint(1) | 否 | 是否已读 | 0-未读, 1-已读 |
| `created_at` | timestamp | 是 | 通知时间 | 默认: CURRENT_TIMESTAMP |

---

### 14. 用户会话表 (`user_sessions`)

管理用户登录 Session 和 Token。

| 字段名 | 类型 | 必填 | 说明 | 索引/备注 |
|---|---|---|---|---|
| `id` | bigint(20) | 是 | 会话ID | **主键**, 自增 |
| `user_id` | bigint(20) | 是 | 用户ID | 索引 (`idx_user_id`) |
| `token` | varchar(255) | 是 | 访问令牌 | **唯一索引** (`token`) |
| `refresh_token` | varchar(255) | 否 | 刷新令牌 | |
| `expires_at` | timestamp | 是 | 过期时间 | 索引 (`idx_expires_at`) |
| `user_agent` | text | 否 | 用户代理 | |
| `is_active` | tinyint(1) | 否 | 是否激活 | 默认: 1 |
| `created_at` | timestamp | 是 | 创建时间 | 默认: CURRENT_TIMESTAMP |
| `updated_at` | timestamp | 是 | 更新时间 | 默认: ON UPDATE CURRENT_TIMESTAMP |

---

### 15. 审核表 (`audit`)

内容及用户审核记录。

| 字段名 | 类型 | 必填 | 说明 | 索引/备注 |
|---|---|---|---|---|
| `id` | bigint(20) | 是 | 审核ID | **主键**, 自增 |
| `user_id` | bigint(20) | 是 | 用户ID | 索引 (`idx_user_id`) |
| `type` | tinyint(4) | 是 | 审核类型 | 1-用户, 2-内容, 3-评论 |
| `content` | text | 是 | 审核内容 | |
| `status` | tinyint(1) | 否 | 审核状态 | 0-待审核, 1-通过 |
| `created_at` | timestamp | 是 | 创建时间 | 默认: CURRENT_TIMESTAMP |
| `audit_time` | timestamp | 否 | 审核时间 | |
