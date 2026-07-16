# 后台管理

## 认证

管理员通过以下入口登录：

```http
POST /api/auth/admin/login
```

`GET /api/auth/admin/me` 返回当前管理员信息。后台请求需要携带管理员 JWT，资源权限由后台认证逻辑和 Spring Security 共同控制。

## 资源管理

后台资源统一使用 `/api/admin/*`：

| 路径 | 管理内容 |
| --- | --- |
| `/admin/admins` | 管理员账号 |
| `/admin/audit` | 审核记录、通过和驳回 |
| `/admin/categories` | 分类 |
| `/admin/tags` | 标签 |
| `/admin/users` | 用户 |
| `/admin/posts` | 帖子、批量删除和状态变更 |
| `/admin/comments` | 评论 |
| `/admin/likes` | 点赞关系 |
| `/admin/collections` | 收藏关系 |
| `/admin/follows` | 关注关系 |
| `/admin/notifications` | 通知 |
| `/admin/sessions` | 用户会话 |

多数资源提供分页列表、详情、新增、修改、单条删除和批量删除。具体筛选参数及 DTO 以对应 `controller/admin/` 代码为准。

后台接口使用 `AdminResult` 作为统一响应封装。前端仍需同时处理 HTTP 状态、业务状态和错误消息。

## 监控

```http
GET /api/admin/monitor/activities
```

该接口为后台活动监控入口。它用于展示当前实现可获取的活动数据，不应被当作完整的基础设施指标或审计日志系统。

## 搜索和 RAG 运维

管理员可手动触发两套独立的增量同步：

```http
POST /api/admin/search/sync
POST /api/admin/rag/sync
```

- 搜索同步返回 `syncedCount`。
- RAG 同步返回 `indexedChunkCount`。
- 两个接口均要求 `ROLE_ADMIN`。
- 同步状态保存在 MySQL `posts` 表，而不是后台页面或 Elasticsearch 临时字段中。

详细规则见 [SEARCH_RAG.md](SEARCH_RAG.md)。

## 管理操作原则

- 优先通过后台 Service 执行写操作，避免绕过关联清理、缓存失效和索引同步。
- 批量删除和状态变更应在界面明确展示影响范围。
- 管理员密码必须使用与认证服务一致的密码编码方式。
- 后台账号、真实 Token 和数据导出不得写入文档或示例配置。
