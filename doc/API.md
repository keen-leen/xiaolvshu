# API 概览

## 基础约定

- 基础地址：`http://localhost:8080/api`
- 普通 JSON 响应使用 `Result<T>`：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

- 登录后通过 `Authorization: Bearer <token>` 传递 JWT。
- 请求和响应 JSON 默认使用 `snake_case`。
- 旅行助手使用 `text/event-stream`，不返回普通 `Result<T>`。

## 访问控制

无需登录即可访问的主要接口：

- `POST /auth/register`、`POST /auth/login`、`POST /auth/refresh`
- `GET /auth/captcha`、`GET /auth/check-user-id`、`GET /auth/health`
- `GET /categories/**`
- `GET /posts`、`GET /posts/**`
- `GET /users`、`GET /users/**`
- `GET /comments/{commentId}/replies`
- `GET /search`
- `POST /ai/travel/**`

其他接口默认需要登录。`POST /admin/search/sync` 和 `POST /admin/rag/sync` 明确要求管理员角色；后台接口还会执行后台管理认证。

## 用户端接口索引

| 模块 | 路径 | 说明 |
| --- | --- | --- |
| 认证 | `/auth` | 注册、验证码、用户 ID 检查、登录、当前用户、刷新、退出、健康检查 |
| 管理员认证 | `/auth/admin` | 管理员登录、当前管理员和管理员账号维护 |
| 分类 | `/categories` | 分类列表 |
| 标签 | `/tags` | 标签列表与热门标签 |
| 帖子 | `/posts` | 发布、修改、删除、列表、详情、评论、搜索和收藏 |
| 评论 | `/comments` | 回复列表、发表评论、删除评论 |
| 点赞 | `/likes` | 点赞与取消点赞 |
| 用户 | `/users` | 搜索、资料、帖子、收藏、点赞、关注关系、统计、认证申请 |
| 通知 | `/notifications` | 未读数、分类通知、已读和删除 |
| 搜索 | `/search` | Elasticsearch 全文搜索 |
| 上传 | `/upload` | 单图、多图和视频上传 |
| 统计 | `/stats` | 社区统计 |
| 旅行助手 | `/ai/travel/chat` | SSE 流式旅行规划 |

常用帖子接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/posts` | 帖子列表 |
| `GET` | `/posts/{id}` | 帖子详情 |
| `POST` | `/posts` | 发布帖子 |
| `PUT` | `/posts/{id}` | 修改帖子 |
| `DELETE` | `/posts/{id}` | 删除帖子 |
| `GET` | `/posts/{postId}/comments` | 帖子评论 |
| `POST` | `/posts/{id}/collect` | 切换收藏状态 |
| `GET` | `/search` | 全文检索帖子 |

常用通知接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/notifications/unread-count` | 当前用户未读总数 |
| `GET` | `/notifications/unread-count-by-type` | 按类型统计未读数 |
| `GET` | `/notifications/comments` | 评论通知 |
| `GET` | `/notifications/likes` | 点赞通知 |
| `GET` | `/notifications/follows` | 关注通知 |
| `GET` | `/notifications/collections` | 收藏通知 |
| `PUT` | `/notifications/{id}/read` | 标记单条已读 |
| `PUT` | `/notifications/read-all` | 全部标记已读 |

## 后台接口

后台资源统一位于 `/admin/*`，覆盖管理员、审核、分类、收藏、评论、关注、点赞、通知、帖子、会话、标签和用户。详细说明见 [ADMIN.md](ADMIN.md)。

后台 Controller 使用 `AdminResult` 响应封装，语义与普通结果封装一致。接口字段的最终定义以对应 DTO 和 Controller 为准。

## 错误处理

调用方必须同时处理 HTTP 状态和响应体中的 `code/message`。出现 `401` 或 `403` 时，应优先检查：

1. 是否携带 `Bearer` Token。
2. Token 是否过期、被刷新或已退出。
3. 当前账号是否具备管理员角色。
4. 请求路径是否误用了公开接口的相似路径。
