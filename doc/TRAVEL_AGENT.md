# 旅行规划 Agent

当前实现基于 Spring Boot 4.1.0 与 Spring AI 2.0.0。框架升级、配置迁移、兼容风险和回滚说明见
[Spring AI 2.0 升级报告](SPRING_AI_2_UPGRADE_REPORT.md)。

## 接口

旅行助手通过 SSE 返回执行过程和最终答案：

```http
POST /api/ai/travel/chat
Content-Type: application/json
Accept: text/event-stream
```

请求包含用户消息和可选的最近对话历史、召回数量等字段，具体字段以 `TravelChatRequest` 为准。该接口当前允许未登录访问。

## 执行模型

后端基于 Spring AI 2.0 原生 Tool Calling 构建“框架解析、应用控制”的 Agent 循环，
不再要求模型输出自定义 JSON，也不会让模型直接控制任意 Java 方法：

1. Spring AI 根据 `@Tool` 生成 JSON Schema，并解析模型返回的原生 `tool_calls`。
2. 应用校验工具白名单、参数、单轮数量、总数量和重复调用。
3. 通过 `ToolCallingManager` 执行通过校验的工具，并把标准工具响应写回对话。
4. 信息足够、出现重复调用或达到步数上限时结束工具循环。
5. 调用模型流式生成最终答案，返回社区引用并用结构化终态结束 SSE。

当前限制：

- 最多保留 8 条历史消息。
- 当前问题和单条历史最多 2000 字符，历史总长度最多 12000 字符。
- 最多执行 5 个 Agent 步骤。
- 单轮最多接受 3 个工具调用，单次运行最多接受 8 个工具调用。
- 单次工具调用超时 3 秒。
- 单次 Agent 默认总超时 120 秒，每 15 秒发送一次 SSE 心跳。
- 重复的“工具名 + 参数”组合会被跳过。
- Agent 请求和工具请求使用有界线程池，过载时返回可读错误。
- 匿名用户默认每分钟 5 次，登录用户每分钟 20 次，单实例最多 8 个在途 Agent 请求。
- `step.thought` 是后端生成的状态文案，不是模型原始推理过程。

## 工具

| 工具 | 职责 | 当前数据性质 |
| --- | --- | --- |
| `search_community_notes` | 检索攻略、路线、景点、美食和避坑笔记 | Elasticsearch RAG 索引中的社区内容 |

天气、价格和预算模拟工具已移除。在接入真实 Provider 之前，Agent 必须明确说明没有实时数据源，
只能给出带假设条件的通用建议，不得生成伪实时数值。

社区笔记工具通过 RAG 混合检索获取上下文和引用，详见 [SEARCH_RAG.md](SEARCH_RAG.md)。

## SSE 事件

| 事件 | 内容 |
| --- | --- |
| `meta` | 首个事件，包含 `runId` 和 `protocolVersion: 2` |
| `step` | 当前步骤、动作和后端安全状态；工具步骤包含 `toolCall` 和精简后的 `toolResult` |
| `chunk` | 最终答案的增量文本 |
| `refs` | 去重后的社区笔记引用；每条包含与正文 `[S1]` 对应的 `sourceId` |
| `error` | 结构化失败终态：`code`、`message`、`retryable`、`runId` |
| `done` | 结构化成功终态：`runId`、`finishReason`、`elapsedMs` |

前端应按事件类型处理，不应将所有 `data` 拼接为正文。收到 `error` 或连接异常时要结束加载状态；收到 `done` 后完成当前消息。
所有业务事件都带递增 SSE `id`；心跳使用 SSE 注释，不会进入正文。当前协议不启用浏览器自动重连，
因为 `POST` 请求无法仅依靠 `Last-Event-ID` 安全重放。前端通过 `fetch` + `AbortController` 主动停止，
并以约 40ms 的窗口合并文本片段，减少 Markdown 全量重绘但保持可见的流式输出。
工具调用参数只位于 `step.toolCall.arguments`；`step.toolResult` 只保留工具名、成功状态、检索上下文、错误和耗时。
社区笔记引用只由最终 `refs` 事件发送，不再在 `toolResult` 中重复携带。
如果请求在建立 SSE 前被拒绝，接口会返回真实 HTTP 状态：参数错误为 `400`，额度或并发超限为 `429`，Redis 限流依赖不可用为 `503`。

## 配置

模型和 RAG 配置来自环境变量：

- `AI_API_KEY`
- `AI_BASE_URL`
- `AI_CHAT_MODEL`
- `AI_TEMPERATURE`
- `AI_EMBEDDING_MODEL`
- `AI_EMBEDDING_DIMENSIONS`
- `RAG_TOP_K`
- `RAG_VECTOR_DIMENSIONS`
- `RAG_NUM_CANDIDATES`
- `RAG_CANDIDATE_COUNT`
- `RAG_RRF_RANK_CONSTANT`
- `RAG_MAX_CHUNKS_PER_POST`
- `RAG_SIMILARITY_THRESHOLD`
- `RAG_BM25_MIN_SCORE`
- `RAG_BM25_STRONG_SCORE`
- `RAG_VECTOR_STRONG_SIMILARITY`
- `AGENT_MAX_CONCURRENT`
- `AGENT_HEARTBEAT_SECONDS`
- `AGENT_RUN_TIMEOUT_SECONDS`
- `AGENT_TOOL_TIMEOUT_SECONDS`
- `AGENT_RATE_LIMIT_PERIOD_SECONDS`
- `AGENT_RATE_LIMIT_ANONYMOUS_MAX`
- `AGENT_RATE_LIMIT_AUTHENTICATED_MAX`
- `AGENT_TRUST_FORWARDED_HEADERS`

模型密钥只保存在本地或部署环境，不得提交到仓库。
