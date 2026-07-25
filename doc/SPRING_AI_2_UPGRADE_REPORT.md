# Spring AI 2.0 升级报告

## 升级概览

本次升级将后端基础框架迁移到 Spring Boot 4 / Spring AI 2，并同步重构旅行规划 Agent。
升级目标不是单纯替换依赖版本，而是移除旧的“提示词要求模型输出 JSON、应用自行截取解析”协议，
改用 Spring AI 原生 Tool Calling，同时继续由应用控制工具授权、资源预算和稳定响应协议。

升级日期：2026-07-22。

| 组件 | 升级前 | 升级后 | 主要影响 |
| --- | --- | --- | --- |
| Spring Boot | 3.3.5 | 4.1.0 | 默认 JSON 栈迁移到 Jackson 3，部分 starter 和自动配置包名变化 |
| Spring AI | 1.0.0 | 2.0.0 | 配置键调整，Agent 使用原生 Tool Calling API |
| MyBatis-Plus | 3.5.7 Boot 3 starter | 3.5.17 Boot 4 starter | `ServiceImpl` 包名和可选模块发生变化 |
| Jackson | 2.x | 3.x | Java 包名、Redis 与 RabbitMQ JSON 转换器调整 |
| Elasticsearch | 8.19.6 服务端 | Java Client 9.4.2 + Elasticsearch 9.4.2 | 主版本对齐；dense vector 枚举及 kNN 整数参数签名调整 |

Java 仍使用 21，MySQL、Redis、RabbitMQ 和 Elasticsearch 的业务职责没有变化。

## 依赖与配置迁移

### Maven 依赖

- Spring Boot parent 更新为 `4.1.0`，Spring AI BOM 更新为 `2.0.0`。
- MyBatis-Plus 改用 `mybatis-plus-spring-boot4-starter:3.5.17`。
- MyBatis-Plus 3.5.9 之后部分能力改为可选模块，因此显式加入
  `mybatis-plus-extension` 和 `mybatis-plus-jsqlparser`，分别提供通用 Service 扩展与分页 SQL 解析能力。
- Boot 4 使用 `spring-boot-starter-aspectj` 替代旧的 AOP starter 名称。
- 增加 Actuator，为 Agent 端到端运行耗时提供 Micrometer 注册基础。
- 增加 `spring-ai-starter-model-chat-memory-repository-jdbc`，由 MySQL 持久化短期对话窗口。
- COS SDK 的可选 KMS 扩展会传递引入 Okio 1.12，与 Spring AI 使用的 OkHttp 4.12 / Okio 3.6
  二进制不兼容。项目未使用 KMS 客户端加密，因此在 `cos_api` 上排除该扩展，普通上传、删除和 URL
  生成功能保持不变。依赖树最终只保留 Okio 3.6，避免启动 `OpenAiEmbeddingModel` 时出现
  `okio.Options NoSuchFieldError`。

### Spring AI 配置

Spring AI 2 将常用模型选项提升为直接属性：

```yaml
spring:
  ai:
    openai:
      chat:
        model: ${AI_CHAT_MODEL:qwen-plus}
        temperature: ${AI_TEMPERATURE:0.3}
      embedding:
        model: ${AI_EMBEDDING_MODEL:text-embedding-v4}
        dimensions: ${AI_EMBEDDING_DIMENSIONS:1024}
```

旧的 `chat.options.model`、`chat.options.temperature`、`embedding.options.model` 和
`embedding.options.dimensions` 不再作为本项目配置源。部署环境继续使用原有环境变量名称，
因此不需要修改密钥或模型环境变量。

新增 Agent 生命周期配置：

| 环境变量 | 默认值 | 语义 |
| --- | ---: | --- |
| `AGENT_HEARTBEAT_SECONDS` | 15 | SSE 心跳间隔，用于刷新代理空闲计时并探测断连 |
| `AGENT_RUN_TIMEOUT_SECONDS` | 120 | 单次 Agent 从建立会话到终止的总时限 |
| `AGENT_MAX_TOOL_CALLS` | 3 | 单次请求真正执行社区检索的上限 |
| `AGENT_MEMORY_MAX_MESSAGES` | 20 | 每个会话提供给模型的最近消息窗口 |
| `AGENT_MEMORY_RETENTION_DAYS` | 30 | 不活跃会话的数据库保留天数 |

### Jackson、RabbitMQ 与 Redis

- 业务 JSON 改用 `tools.jackson.*` 的 Jackson 3 API。
- RabbitMQ 使用 `JacksonJsonMessageConverter`，替代带 Jackson 2 版本标识的转换器。
- Redis 使用 `GenericJacksonJsonRedisSerializer`。`RedisTemplate<String, Object>` 为恢复不同 DTO
  仍启用多态类型信息，因此只允许反序列化应用自己写入的数据，不能接收用户直接提交的 JSON。
- 生产升级前应在预发环境抽查旧 Redis 缓存和 RabbitMQ 存量消息；缓存可按业务 TTL 自然淘汰，
  队列消息则应确认消费完成或验证字段兼容后再切换。

### MyBatis-Plus 与 Elasticsearch

- 业务 Service 的基类迁移到 `com.baomidou.mybatisplus.spring.service.impl.ServiceImpl`。
- 纯单元测试不会启动 MyBatis 容器，使用 Lambda wrapper 的测试需要显式初始化实体表元数据；
  这只影响测试夹具，不改变运行时 SQL 和数据库结构。
- dense vector similarity 改用 `DenseVectorSimilarity.Cosine` 枚举。
- kNN 查询的 `k` 和 `numCandidates` 按新客户端签名传入 `int`，现有配置值和检索公式不变。
- 本次没有修改 Elasticsearch 索引维度、分词器、RRF 参数或重建规则，不需要因框架升级重建索引。

## Agent 架构变化

### 决策与工具执行

升级前由提示词要求模型返回自定义 JSON，再通过字符串解析得到动作。当前重构后：

1. Spring AI 从 `@Tool` 和 `@ToolParam` 生成工具 JSON Schema。
2. `ChatClient` 作为唯一模型调用入口。
3. `MessageChatMemoryAdvisor` 按会话 ID 加载和保存最近消息。
4. `ToolCallingAdvisor` 解析原生 `tool_calls`，执行工具并自动回填标准工具响应，直到产生最终回答。
5. 请求级 `ToolContext` 保存服务端状态；模型看不到身份、SSE session、引用注册表和成本上限。
6. `TravelAgentRunContext` 限制 `topK` 与真实检索次数，并把多轮检索中重复的局部 `[S1]`
   转换为整次请求稳定的全局来源编号。

Spring AI 2.0 底层的 OpenAI Java SDK 会相对 `base-url` 追加 `/chat/completions`。
DashScope OpenAI 兼容模式的 API 根路径因此调整为
`https://dashscope.aliyuncs.com/compatible-mode/v1`；如果沿用不含 `/v1` 的旧值，
模型请求会命中错误路径并返回 `404 Unknown`。

当前只开放 `search_community_notes`。天气、实时价格、票务和预算模拟工具没有重新引入；
回答涉及实时信息时必须明确当前没有实时数据源。

默认资源边界：单次运行最多执行 3 次真实社区检索、总超时 120 秒、消息窗口 20 条。
工具循环与标准消息协议由 Spring AI 维护，应用不再保留手工步骤、工具 Future 和重复 DTO。

### JDBC ChatMemory

浏览器不再上传 `history`。首次聊天由后端签发 UUID，后续只回传该 `conversationId`。
数据库 key 额外加入 `userId` 或 `anonymous` 命名空间，避免两个登录用户因 UUID 相同而共享上下文。
刷新页面时前端通过消息查询接口恢复窗口内的 user/assistant 消息；“重新聊聊”会同步清空数据库记忆。

本次新增 `SPRING_AI_CHAT_MEMORY` 表。新开发库已写入初始化 schema，已有环境必须执行
`scripts/migrate-agent-chat-memory.sql`。

### SSE v4

业务事件顺序为：

```text
meta -> status* -> chunk* -> refs -> done
                             `-> error（任意失败终态）
```

- `meta` 包含 `runId`、`protocolVersion: 4` 和后端签发的 `conversationId`。
- `status` 只包含 `thinking/searching/writing` 等后端进度，不暴露模型原始推理。
- 不再发送 SSE `id`：前端从未消费该字段，且 POST 流无法使用 `EventSource` 的
  `Last-Event-ID` 自动重连语义。心跳继续使用 SSE 注释，不进入正文。
- `error` 是包含 `code`、`message`、`retryable`、`runId` 的结构化失败终态。
- `done` 是包含 `runId`、`finishReason`、`elapsedMs` 的结构化成功终态。
- 社区上下文使用 `[S1]`、`[S2]` 来源编号，最终 `refs.source_id` 与正文编号对应。

当前接口仍是带 JSON 请求体的 `POST`，因此前端使用 `fetch` 手动解析 SSE，不启用依赖 GET 的
原生 `EventSource` 自动重连。连接在没有 `done/error` 时结束会被判定为异常，避免展示不完整答案却标记成功。

### 并发、取消和流式渲染

- `AgentAccessGuard` 在进入模型前限制访问者速率和实例级在途数量。
- `ChatClient.stream()` 直接返回 Reactor 流，不再额外包装 Agent/工具线程池和 Future。
- Controller 直接返回 `Flux<ServerSentEvent<?>>`；Spring MVC 负责订阅、SSE 编码，并在写出失败、
  Servlet 超时或浏览器断连时取消整个上游。
- 工具状态通过请求级 Reactor Sink 合并到模型流，心跳也是普通 Flux；应用不再创建 `SseEmitter`、
  手工 `subscribe` 或持有 `Disposable`。并发许可只在最外层 Flux 终止时释放。
- 浏览器通过 `AbortController` 主动停止；关闭弹窗或卸载页面也会取消当前请求。
- 前端以约 40ms 的窗口合并 token，再更新 Vue 状态和 Markdown，保持流式观感同时减少重复渲染。

## 兼容性与部署检查

### 对外变化

- `/api/ai/travel/chat` 地址不变，请求删除 `history` 并新增可选 `conversation_id`。
- SSE 删除旧 `step` 工具细节事件，改为安全的 `status`；`meta` 升级为协议 v4，并删除事件 ID。
- 新增会话消息查询和清空接口。
- 独立客户端必须保存 `meta.conversation_id`，按事件名解析，并把 `done` 与 `error` 都视为终态。

### 上线前检查

1. 使用新的 JAR 和环境变量启动预发后端，确认 Chat 与 Embedding 模型配置成功绑定。
2. 执行 ChatMemory 数据库迁移，验证首次会话、连续追问、刷新恢复、清空、用户隔离和匿名会话。
3. 验证普通问答、社区检索、总超时、用户停止和代理断连路径。
4. 观察 `xiaolvshu.agent.run.duration` 指标。
5. 检查旧 Redis 缓存和 RabbitMQ 存量消息的反序列化结果。
6. 前后端应同批发布；若存在其他 SSE 客户端，必须先完成 v4 协议和会话 ID 兼容。

### 回滚关注点

代码可整体回滚到升级前版本，但不能只回滚前端或只回滚 Agent 服务，因为 SSE 和请求体已经变化。
回滚代码不需要删除 ChatMemory 表；它是独立新增表，旧版本不会访问。Elasticsearch 无需重建索引。
若回滚到 Jackson 2，应同时恢复 RabbitMQ/Redis 转换器，避免混用两套序列化配置。

## 验证结果

- `mvn clean test`：共发现 55 个测试，54 个执行通过，0 个失败，
  1 个依赖真实外部服务的 RAG 评测按条件跳过。
- 使用 `scripts/start-dev.sh` 和真实开发配置完成启动冒烟验证；Spring AI、COS、MyBatis-Plus、
  Tomcat 与 RabbitMQ 均成功初始化，并完成优雅关闭。
- `npm run build`：构建成功。
- 已知但不由本次升级引入的前端警告：个别图片构建期无法解析、部分模块同时动态和静态导入、
  主 JavaScript chunk 超过 Vite 默认提示阈值。这些警告不阻塞本次升级，但应在后续前端性能任务中处理。
