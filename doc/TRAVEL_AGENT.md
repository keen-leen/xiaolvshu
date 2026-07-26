# 小旅书旅行 Agent

当前旅行助手使用 Spring AI 2.0 的标准应用流程：

```text
POST /ai/travel/chat
  -> ChatClient
  -> MessageChatMemoryAdvisor（加载最近对话）
  -> ToolCallingAdvisor（自动完成模型 -> 工具 -> 模型循环）
  -> @Tool search_community_notes / get_weather
  -> 流式答案与社区引用
```

业务代码不再手工解析 `tool_calls`、拼接工具响应或维护递归步骤。Spring AI Advisor 负责协议循环，
小旅书代码只处理身份隔离、RAG 成本边界、引用编号、响应事件编排和错误协议。

## 主要类

| 类 | 职责 |
| --- | --- |
| `TravelAgentConfiguration` | 创建 `ChatClient`、`MessageChatMemoryAdvisor`、`ToolCallingAdvisor` 和消息窗口 |
| `TravelAgentService` | 返回 SSE Flux、设置请求级 ToolContext、合并模型/工具/心跳事件、处理总超时与终态 |
| `TravelAgentTools` | 统一声明社区检索和天气 `@Tool`，只处理工具参数与服务转发 |
| `WeatherService` | 调用 Open-Meteo，并完成地点查询、当前天气和 7 日预报 |
| `TravelAgentRunContext` | 限制单次社区检索次数、统一多次检索的 `[S1]` 编号、按需生成引用规则并收集最终引用 |
| `TravelAgentConversationService` | 签发会话 ID、隔离用户、恢复/清空 JDBC ChatMemory、清理过期会话 |
| `AgentAccessGuard` | 访问者限流和实例级在途请求保护 |

当前开放 `search_community_notes` 和 `get_weather`。两个工具统一声明在 `TravelAgentTools`；
RAG 查询由 `RagService` 实现，天气查询由 `WeatherService` 调用 Open-Meteo Geocoding API 和
Forecast API。地点无结果或请求失败时返回简短失败信息，模型不得猜测。
票务、价格和营业状态没有真实数据源，不能声称已经查询。

## 天气工具

`get_weather` 只接收一个地点参数。`TravelAgentTools` 将参数转给 `WeatherService`，后者先通过
Open-Meteo Geocoding API 获取经纬度和时区，再通过一次 Forecast API 请求取得当前天气和未来
7 日预报，并将 WMO 天气代码整理为中文文本。当前使用无需密钥的非商业开放接口，实现只保留
3 秒连接/读取超时，不加入缓存、重试、地点消歧或 Provider 框架。

## 亮点：RAG 成功后按需加载引用规则

引用约束没有全部固化在系统提示词中。第一次模型请求只携带通用原则和
`search_community_notes` 的工具定义，让模型先判断当前问题是否需要社区事实；只有工具成功返回
至少一个可追溯的笔记引用后，`TravelAgentRunContext` 才在工具结果中动态追加具体引用规则：

```text
第一次模型请求
  -> 基础系统提示 + 用户问题 + 工具定义
  -> 模型按需调用 search_community_notes
  -> 注册并统一来源编号
  -> 工具结果 = 不可信社区资料 + 应用生成的引用规则 + 本次合法尾注集合
  -> 模型生成带句末尾注的最终正文
```

动态规则把尾注定义为证据标记，而不是必须凑齐的回答格式。模型要先判断笔记正文片段是否包含
与问题直接相关的具体事实；标题、标签和泛化感想只能帮助定位资料，不能单独支持餐厅、路线、价格、
时间或交通等细节。如果存在可直接使用的证据，正文必须至少采用一条并在对应句末标注 `[Sx]`；
如果所有片段都缺少具体信息，则不标尾注，并明确说明后续建议主要基于通用旅行知识。

模型只能使用工具结果明确列出的合法编号，不能虚构尾注，也不能把尾注随意标在来源没有直接支持的
内容后面。社区正文位于 `UNTRUSTED COMMUNITY NOTES` 边界内，应用生成的引用规则位于边界外，
避免社区内容被当成指令执行。

合法尾注集合和最终 `refs` 事件共用同一个运行级引用注册表。一次 Agent 运行内多次检索时，
每次 RAG 从 `[S1]` 开始的局部编号都会转换成稳定的全局编号，并按 `postId` 去重；后续工具结果
会给模型提供累计合法集合。`refs` 表示本次检索到的候选笔记，正文实际采用的 `[Sx]` 是其中有
直接证据的子集；每个正文编号仍能与对应引用卡片和笔记链接一致。空检索结果没有有效引用，不会
加载引用规则，也不会诱导模型生成不存在的 `[S1]`。

这一设计同时保留了三个特性：普通聊天不承担无关引用提示，有直接证据的 RAG 回答具有正文级来源
痕迹，多次工具调用仍保持来源编号稳定；整个过程由标准 `ToolCallingAdvisor` 工具循环完成，
不需要缓冲最终正文，因此不会牺牲实时流式输出。

## 会话记忆

首次请求可以不传 `conversationId`，后端会生成随机 UUID，并在 `meta.conversation_id` 返回。
后续请求只上传当前问题和该 ID，不再由浏览器上传可篡改、重复且不断膨胀的 `history`。

真正写入 MySQL 的 key 会附加身份命名空间：

```text
登录用户：travel-agent:user:{userId}:{conversationId}
匿名用户：travel-agent:anonymous:{conversationId}
```

相同 UUID 在不同登录用户下会映射到不同记忆。匿名 UUID 是高熵会话凭证，前端只保存在当前浏览器
的 `localStorage`。消息窗口默认保留 20 条；不活跃会话默认 30 天后整段删除。

已有数据库需执行：

```bash
mysql ... < scripts/migrate-agent-chat-memory.sql
```

新开发数据库的 `docker/dev/mysql/init/schema.sql` 已包含该表。

## HTTP 接口

### 流式对话

```http
POST /api/ai/travel/chat
Content-Type: application/json
Accept: text/event-stream

{
  "message": "帮我安排杭州三日亲子游",
  "topK": 5,
  "conversationId": "可选，由上一次 meta 返回"
}
```

### 恢复最近消息

```http
GET /api/ai/travel/conversations/{conversationId}/messages
```

### 清空当前会话

```http
DELETE /api/ai/travel/conversations/{conversationId}
```

三个接口均允许匿名访问，但仍按登录身份或匿名会话隔离。聊天入口还会执行 Redis 速率限制和实例并发限制。

## SSE v4

正常顺序为：

```text
meta -> status* -> chunk* -> refs -> done
                         `-> error
```

| 事件 | 内容 |
| --- | --- |
| `meta` | `run_id`、`protocol_version: 4`、后端签发的 `conversation_id` |
| `status` | `thinking`、`searching`、`weather_searching`、`writing` 等简短进度；不是模型思维过程 |
| `chunk` | 最终回答的增量文本 |
| `refs` | 去重后的社区笔记引用，`source_id` 与正文 `[S1]` 一一对应 |
| `done` | `finish_reason`、`elapsed_ms` 等成功终态 |
| `error` | `code`、安全错误文案、`retryable` 等失败终态 |

v4 不再发送无消费方、也无法支持 POST 自动重连的 SSE `id`。心跳使用 SSE 注释，不会进入正文。
Controller 直接返回 `Flux<ServerSentEvent<?>>`，Spring MVC 负责订阅、SSE 编码和断连取消；应用不再
创建 `SseEmitter`、手工 `subscribe` 或保存 `Disposable`。前端使用 `fetch + AbortController`
解析 POST SSE，并以约 40ms 批量写入 token。最终回答开始输出前，前端会按接收顺序展开显示
`status` 过程；收到第一个正文 `chunk` 后收起状态区。弹窗与完整页面共用一个 Pinia store，
所以切换入口不会维护两份历史、状态过程或协议代码。

## 边界与配置

- 消息最多 2000 字，`topK` 为 1～10。
- 每次运行默认最多真实执行 3 次社区检索。
- 默认总超时 120 秒，每 15 秒发送一次心跳。
- 匿名用户默认每分钟 5 次，登录用户每分钟 20 次，单实例最多 8 个在途请求。
- 社区内容视为不可信资料，不能覆盖系统指令。
- 原始模型推理、工具内部上下文、异常堆栈和鉴权信息均不会通过 SSE 暴露。

相关环境变量：

```text
AGENT_MAX_CONCURRENT
AGENT_HEARTBEAT_SECONDS
AGENT_RUN_TIMEOUT_SECONDS
AGENT_MAX_TOOL_CALLS
AGENT_RATE_LIMIT_PERIOD_SECONDS
AGENT_RATE_LIMIT_ANONYMOUS_MAX
AGENT_RATE_LIMIT_AUTHENTICATED_MAX
AGENT_TRUST_FORWARDED_HEADERS
AGENT_MEMORY_MAX_MESSAGES
AGENT_MEMORY_RETENTION_DAYS
AGENT_MEMORY_CLEANUP_CRON
```

模型、Embedding 和检索阈值仍使用现有 `AI_*`、`RAG_*` 配置，RAG 细节见
[SEARCH_RAG.md](SEARCH_RAG.md)。
