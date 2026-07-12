# 旅行攻略 Agent

旅行助手已从“固定先做 RAG，再调用模型”改为 tool-style Agent。社区笔记检索、天气、价格和预算都按工具方式组织，模型按用户问题决定调用顺序，后端负责执行工具、限步、去重、超时和 SSE 轨迹。

## Agent 循环

每次请求最多执行 5 步：

1. 模型读取用户问题、历史对话和已获得的工具结果。
2. 模型选择调用一个工具，或判断信息足够后进入最终生成。
3. 后端执行工具，把结果作为观察追加到 Agent 上下文。
4. 后端跳过相同工具和相同参数的重复调用。
5. 工具失败或 3 秒超时后，把失败原因作为观察交给最终生成。

Agent 会边执行边推送 SSE 事件，而不是等所有步骤完成后一次性返回。事件包括：

- `step`：Agent 步骤和选择的动作。
- `tool`：工具调用结果。
- `chunk`：最终答案文本。
- `refs`：社区笔记引用。
- `done`：完成。

## 为什么 RAG 是工具

RAG 不再是固定前置步骤。天气问题可以先查天气，预算问题可以先估价格，完整攻略和路线类问题通常优先检索社区笔记。这样可以避免无关检索，也能让模型在拿到天气或价格后再决定是否需要社区笔记。

## 工具列表

当前工具方法保留 Spring AI `@Tool` 注解作为工具 schema 元信息和后续接入原生 tool-calling 的准备；本版本仍采用手动 Agent loop，以便保留可观测的 `step` / `tool` SSE、去重和超时控制。

### `search_community_notes`

检索小旅书社区笔记。

参数：`query`、`destination`、`interests`、`topK`。

返回：检索 query、上下文文本、引用笔记列表。引用包含标题、作者、摘要、标签、链接和 `postId`。

### `get_weather_forecast`

查询目的地天气。第一版为 mock/provider 适配层。

参数：`destination`、`startDate`、`days`。

返回：日期、天气、温度、降雨提示、出行提醒和 provider 说明。

### `search_travel_prices`

估算交通、住宿、餐饮、门票等价格。第一版使用规则估算。

参数：`destination`、`origin`、`startDate`、`days`、`travelers`、`budgetLevel`。

返回：价格项、价格区间、估算规则和数据源说明。

### `estimate_trip_budget`

在实时价格不足时生成预算拆分。

参数：`destination`、`days`、`travelers`、`travelStyle`。

返回：复用价格估算结构，按住宿、餐饮、交通、门票拆分。

## 提示词策略

系统提示要求模型不要编造实时天气、价格、营业状态等信息；攻略、路线、景点、美食、避坑、小众玩法等问题优先调用社区笔记工具；最终答案必须区分社区笔记依据、工具查询结果和通用经验补充。

最终输出固定包含：

- 行程规划
- 预算建议
- 避坑提醒
- 可选替代方案

## 降级规则

- `maxSteps=5`，达到上限后基于已有结果生成答案。
- 同一工具和相同参数只执行一次。
- 工具超时为 3 秒。
- 工具失败不会无限重试，失败原因会进入最终答案上下文。
- mock 天气和规则价格必须在答案中体现不确定性。

## 示例轨迹

“明天去杭州穿什么”

1. `get_weather_forecast`
2. 视问题需要补充 `search_community_notes`
3. 最终答案说明天气工具来源和穿衣建议。

“成都三天预算 2000 怎么玩”

1. `search_travel_prices`
2. `search_community_notes`
3. 最终答案结合预算拆分和社区路线。

“上海小众拍照路线”

1. `search_community_notes`
2. 最终答案基于社区笔记生成拍照路线和替代方案。

## 后续真实 Provider 接入

`TravelAgentTools` 中的天气和价格工具目前是 mock/规则实现。接入真实 API 时保留工具签名不变，只替换 provider 层逻辑即可，Agent 循环、SSE 事件和前端展示不需要改动。

后续如果要改为 Spring AI 原生工具调用，需要基于 `ChatResponse.hasToolCalls()` 处理模型 tool call，并重新设计步骤事件、去重和超时控制；在这之前保持手动 tool-style Agent 更稳定。
