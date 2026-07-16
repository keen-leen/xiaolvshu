# 旅行规划 Agent

## 接口

旅行助手通过 SSE 返回执行过程和最终答案：

```http
POST /api/ai/travel/chat
Content-Type: application/json
Accept: text/event-stream
```

请求包含用户消息和可选的最近对话历史、召回数量等字段，具体字段以 `TravelChatRequest` 为准。该接口当前允许未登录访问。

## 执行模型

后端使用手动 Agent 循环，而不是让模型直接控制任意 Java 方法：

1. 根据问题和已有工具结果决定下一步动作。
2. 执行白名单工具并把结果写回上下文。
3. 信息足够、出现重复调用或达到步数上限时结束工具循环。
4. 调用模型流式生成最终答案。
5. 返回社区笔记引用并结束 SSE。

当前限制：

- 最多保留 8 条历史消息。
- 最多执行 5 个 Agent 步骤。
- 单次工具调用超时 3 秒。
- 重复的“工具名 + 参数”组合会被跳过。
- Agent 请求和工具请求使用有界线程池，过载时返回可读错误。

## 工具

| 工具 | 职责 | 当前数据性质 |
| --- | --- | --- |
| `search_community_notes` | 检索攻略、路线、景点、美食和避坑笔记 | Elasticsearch RAG 索引中的社区内容 |
| `get_weather_forecast` | 查询目的地日期范围天气 | 规则模拟 Provider，不是实时天气 |
| `search_travel_prices` | 估算住宿、餐饮、市内交通和门票 | 规则估算，不含实时票价 |
| `estimate_trip_budget` | 按目的地、天数、人数和风格拆分预算 | 复用规则价格估算 |

天气和价格结果必须明确标注其估算性质，最终答案不能把模拟数据描述为实时事实。

社区笔记工具通过 RAG 混合检索获取上下文和引用，详见 [SEARCH_RAG.md](SEARCH_RAG.md)。

## SSE 事件

| 事件 | 内容 |
| --- | --- |
| `step` | 当前步骤、动作、思路及工具信息 |
| `tool` | 工具执行结果、耗时、状态和可能的引用 |
| `chunk` | 最终答案的增量文本 |
| `refs` | 去重后的社区笔记引用 |
| `error` | 过载、模型或工具异常时的可读错误 |
| `done` | `[DONE]`，表示正常流结束 |

前端应按事件类型处理，不应将所有 `data` 拼接为正文。收到 `error` 或连接异常时要结束加载状态；收到 `done` 后完成当前消息。

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

模型密钥只保存在本地或部署环境，不得提交到仓库。
