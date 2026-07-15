# 旅行助手 Agent 变更记录

## 2026-07-14 全文索引状态与服务解耦

- `posts` 新增 `is_indexed/indexed_at`，全文索引不再复用向量化状态。
- 全文索引收敛到 `SearchIndexService`，RAG 只管理 chunk、embedding 和混合召回。
- 新增管理员接口 `POST /admin/search/sync`，只补偿待同步的全文文档。
- 管理员 JWT 新增 `principalType=ADMIN`，旧管理员令牌需重新登录。

## 2026-05-14

### RAG 笔记切分

- 将旅行助手 RAG 从“一篇笔记一个向量”升级为“短笔记单 chunk、长笔记多 chunk”。
- 同步流程仍使用现有 pgvector、Spring AI `VectorStore` 和 `ragVectorStore.add()`，旧向量继续按 `postId` 批量删除。
- 长正文优先按换行、空行和列表行形成自然段，单段过长时再按 `。！？；` 等标点拆分。
- 过短段落会合并为一个 chunk，目标长度约 500～800 个中文字符，相邻 chunk 保留约 80 字 overlap。
- 每个向量化文本都保留标题、作者、标签和当前片段正文，避免 chunk 脱离全局上下文。
- metadata 继续保留 `source`、`postId`、`title`、`author`、`summary`、`link`、`tags`，新增 `chunkIndex`、`chunkCount`、`chunkType=content`。
- 引用映射按 `postId` 去重，避免同一篇笔记的多个 chunk 在前端引用列表重复展示。

### RAG 面试口径

当前系统从一篇笔记一个向量升级为“短笔记单 chunk、长笔记多 chunk”。这样可以避免长攻略被压缩成一个过于宽泛的语义向量，提高针对景点、美食、住宿、交通等细粒度问题的召回精度。每个 chunk 都会携带标题、作者、标签等上下文，metadata 中保留 `postId` 和 `chunkIndex`；更新笔记时按 `postId` 删除旧向量，再写入新的 chunk 向量。

### 前端

- 新增独立旅行助手页面 `/travel-ai`，支持填写目的地、天数、预算、同行人、日期、旅行风格、兴趣偏好和避开内容。
- 新增结构化攻略展示区，展示 AI 生成的 Markdown 攻略、每日路线、预算建议和参考笔记。
- 新增继续追问区域，复用流式对话接口展示实时回复。
- 新增 `stores/travelAi.js`，统一管理全局旅行助手浮窗的打开、关闭和入口传入的初始提示词。
- 重构全局 `TravelAiDialog`，改为主题变量配色，并增加“完整页面”入口。
- 新增用户端入口：桌面侧边栏、移动底栏、笔记详情“AI规划”按钮、发布页“AI整理攻略”按钮。
- 按反馈删除发现页 `ai-guide` 引导按钮组件，保留右下角原旅行 AI 聊天浮窗入口。
- 将浮窗内助手名称统一为“旅行助手”。

### 后端

- 旅行攻略生成统一收敛到 `POST /api/ai/travel/chat` 流式接口。
- 复用现有 RAG 检索、引用笔记映射和 Spring AI 调用，不新增数据库表。
- 结构化接口保持公开访问，沿用 `/ai/travel/**` 的现有鉴权策略。

### 文档

- 新增本文档作为旅行助手 Agent 的集中变更记录。
- 更新 `doc/API_DOCS.md`，补充旅行助手接口说明。

### 验证

- 已通过：`cd vue3-project && npm run build`
- 已通过：`cd java-project && mvn test`
- 已通过：`cd java-project && mvn clean package -DskipTests`
- 已通过：再次执行 `cd vue3-project && npm run build` 验证入口调整。
- 已通过：`cd java-project && mvn test` 验证本次 RAG chunk 改造。
- 已通过：`cd java-project && mvn clean package -DskipTests` 验证本次 RAG chunk 改造可完整重新编译打包。
- 前端构建存在既有资源路径和 chunk size 警告，不影响本次构建结果。
- 后端测试阶段当前没有测试源码，命令完成编译校验并成功结束。
# 2026-07-12 Elasticsearch 统一检索迁移

- 移除 PostgreSQL/pgvector，改用 Elasticsearch 8.x 统一承载笔记全文检索与 RAG dense-vector 检索。
- 新增笔记索引和 chunk 索引；RAG 使用中文 BM25 与 cosine kNN 混合召回。
- `/search` 与 `/posts/search` 的笔记分支改为 ES 相关性排序、过滤、分页和标签聚合，用户搜索仍保留 MySQL。
- `/ai/travel/sync` 支持 `mode=incremental|full`，并在笔记事务提交后同步新增、修改、草稿和删除状态。
- 新增带 SmartCN 插件的 Elasticsearch Compose 部署与迁移、回滚文档。
