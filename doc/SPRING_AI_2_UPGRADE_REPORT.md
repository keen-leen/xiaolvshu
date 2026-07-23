# Spring AI 2.0 升级报告

## 升级概览

本次升级将后端基础框架迁移到 Spring Boot 4 / Spring AI 2，并同步重构旅行规划 Agent。
升级目标不是单纯替换依赖版本，而是移除旧的“提示词要求模型输出 JSON、应用自行截取解析”协议，
改用 Spring AI 原生 Tool Calling，同时继续由应用控制工具授权、资源预算和 SSE 生命周期。

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
- 增加 Actuator，为 Agent 运行耗时、首 token 延迟、工具耗时和 token 数量提供 Micrometer 注册基础。
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
| `AGENT_TOOL_TIMEOUT_SECONDS` | 3 | 单轮工具执行等待上限 |

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

升级前由提示词要求模型返回自定义 JSON，再通过字符串解析得到动作。升级后：

1. Spring AI 从 `@Tool` 和 `@ToolParam` 生成工具 JSON Schema。
2. 模型返回原生 `tool_calls`，Spring AI 负责协议解析。
3. 应用校验工具白名单、参数长度、数组数量、`topK`、单轮数量、总数量和重复调用。
4. 只有通过校验的调用才会交给 `ToolCallingManager`。
5. 标准 `ToolResponseMessage` 回填对话后，模型才能进行下一轮决策。

Spring AI 2.0 的工具接口虽然以 `ToolCallingChatOptions` 抽象暴露，但具体 Provider 在构造请求时
仍会读取自己的 Options 类型。Agent 因此从 `ChatModel.getOptions()` 调用 `mutate()` 派生配置，
再追加工具回调；不能使用通用 `ToolCallingChatOptions.builder()`，否则 OpenAI Provider 会把
`DefaultToolCallingChatOptions` 强转为 `OpenAiChatOptions` 并触发 `ClassCastException`。这种派生方式还会
保留配置文件中的模型、温度、超时和 Provider 扩展字段。

Spring AI 2.0 底层的 OpenAI Java SDK 会相对 `base-url` 追加 `/chat/completions`。
DashScope OpenAI 兼容模式的 API 根路径因此调整为
`https://dashscope.aliyuncs.com/compatible-mode/v1`；如果沿用不含 `/v1` 的旧值，
模型请求会命中错误路径并返回 `404 Unknown`。

当前只开放 `search_community_notes`。天气、实时价格、票务和预算模拟工具没有重新引入；
回答涉及实时信息时必须明确当前没有实时数据源。

默认资源边界：最多 5 个决策步骤、单轮 3 个工具调用、全程 8 个工具调用、8 条历史消息。
工具参数在递归排序和去除字符串首尾空格后生成稳定去重键，避免通过 JSON 字段顺序绕过重复调用限制。

### SSE v2

业务事件顺序为：

```text
meta -> step* -> chunk* -> refs -> done
                           `-> error（任意失败终态）
```

- `meta` 包含 `runId` 和 `protocolVersion: 2`。
- 每个业务事件带递增 SSE `id`，心跳使用 SSE 注释，不进入正文。
- `error` 是包含 `code`、`message`、`retryable`、`runId` 的结构化失败终态。
- `done` 是包含 `runId`、`finishReason`、`elapsedMs` 的结构化成功终态。
- 社区上下文使用 `[S1]`、`[S2]` 来源编号，最终 `refs.source_id` 与正文编号对应。

当前接口仍是带 JSON 请求体的 `POST`，因此前端使用 `fetch` 手动解析 SSE，不启用依赖 GET 的
原生 `EventSource` 自动重连。连接在没有 `done/error` 时结束会被判定为异常，避免展示不完整答案却标记成功。

### 并发、取消和流式渲染

- Agent 主任务池与 `AGENT_MAX_CONCURRENT` 对齐并使用 `SynchronousQueue`，获得许可后不能继续无界排队。
- 工具任务使用独立有界线程池，工具超时或会话结束时取消对应 Future。
- SSE completion、timeout、error、心跳失败可能并发发生，统一用原子关闭状态保证并发许可只释放一次。
- 浏览器通过 `AbortController` 主动停止；关闭弹窗或卸载页面也会取消当前请求。
- 前端以约 40ms 的窗口合并 token，再更新 Vue 状态和 Markdown，保持流式观感同时减少重复渲染。

## 兼容性与部署检查

### 对外变化

- `/api/ai/travel/chat` 地址、请求体和 SSE `step/chunk/refs` 的职责不变。
- `done` 从字符串 `[DONE]` 改为 JSON，`error` 从纯文本改为 JSON，并新增首个 `meta` 事件。
- `TravelToolCall` 新增 `call_id`，引用对象新增 `source_id`。项目自带前端已经兼容这些字段。
- 独立客户端必须按事件名解析，并把 `done` 与 `error` 都视为终态；不能继续把所有 `data` 拼接为正文。

### 上线前检查

1. 使用新的 JAR 和环境变量启动预发后端，确认 Chat 与 Embedding 模型配置成功绑定。
2. 验证普通问答、社区检索、工具超时、用户停止和代理断连五类路径。
3. 观察 `xiaolvshu.agent.run.duration`、`xiaolvshu.agent.time_to_first_token`、
   `xiaolvshu.agent.tool.duration` 和 `xiaolvshu.agent.tokens` 指标。
4. 检查旧 Redis 缓存和 RabbitMQ 存量消息的反序列化结果。
5. 前后端应同批发布；若存在其他 SSE 客户端，必须先完成 v2 终态兼容。

### 回滚关注点

代码可整体回滚到升级前版本，但不能只回滚前端或只回滚 Agent 服务，因为 SSE 终态格式已经变化。
本次没有数据库迁移和 Elasticsearch 索引结构迁移，回滚不需要执行 SQL 或重建索引。
若回滚到 Jackson 2，应同时恢复 RabbitMQ/Redis 转换器，避免混用两套序列化配置。

## 验证结果

- `mvn clean test`：共发现 51 个测试，50 个执行通过，0 个失败，
  1 个依赖真实外部服务的 RAG 评测按条件跳过。
- 使用 `scripts/start-dev.sh` 和真实开发配置完成启动冒烟验证；Spring AI、COS、MyBatis-Plus、
  Tomcat 与 RabbitMQ 均成功初始化，并完成优雅关闭。
- `npm run build`：构建成功。
- 已知但不由本次升级引入的前端警告：个别图片构建期无法解析、部分模块同时动态和静态导入、
  主 JavaScript chunk 超过 Vite 默认提示阈值。这些警告不阻塞本次升级，但应在后续前端性能任务中处理。
