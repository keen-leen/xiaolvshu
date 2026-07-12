package com.xiaolvshu.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaolvshu.dto.CommunitySearchResult;
import com.xiaolvshu.dto.TravelAgentStep;
import com.xiaolvshu.dto.TravelChatRequest;
import com.xiaolvshu.dto.TravelChatResponse;
import com.xiaolvshu.dto.TravelToolCall;
import com.xiaolvshu.dto.TravelToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class TravelAgentService {

    private static final int MAX_HISTORY = 8;
    private static final int MAX_STEPS = 5;
    private static final long TOOL_TIMEOUT_SECONDS = 3L;

    private final TravelAgentTools travelAgentTools;
    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;

    private final ExecutorService streamExecutor = new ThreadPoolExecutor(
            4,
            8,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingDeque<>(20),
            r -> {
                Thread t = new Thread(r);
                t.setName("travel-agent-stream-" + t.threadId());
                return t;
            },
            new ThreadPoolExecutor.AbortPolicy()
    );

    private final ExecutorService toolExecutor = new ThreadPoolExecutor(
            4,
            16,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            r -> {
                Thread t = new Thread(r);
                t.setName("travel-agent-tool-" + t.threadId());
                return t;
            },
            new ThreadPoolExecutor.AbortPolicy()
    );


    /**
     * Agent 统一流式对话入口。
     *
     * @param request 用户问题和最近历史对话
     * @return SSE 发射器
     */
    public SseEmitter chat(TravelChatRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            // 每个请求单独一个 Agent 线程，允许并发处理多个用户对话；工具调用放在 Agent 线程内的独立线程池，避免外部服务调用阻塞整个 Agent 循环。
            streamExecutor.execute(() -> runStreaming(request, emitter));
        } catch (Exception e) {
            log.warn("提交Agent流式任务失败: {}", e.getMessage());
            safeSend(emitter, "error", "当前请求过多，请稍后重试。");
            safeComplete(emitter);
        }
        return emitter;
    }

    /**
     * 在 Agent 线程池中执行流式 Agent 循环。
     * 
     * 每完成一个步骤就立即发送 `step` / `tool` 事件；最终答案也通过模型 stream API 逐 token 推送 `chunk`，让前端真实感知 Agent 执行进度。
     */
    private void runStreaming(TravelChatRequest request, SseEmitter emitter) {
        List<TravelAgentStep> steps = new ArrayList<>();
        List<TravelToolResult> toolResults = new ArrayList<>();
        Set<String> callKeys = new LinkedHashSet<>();

        try {
            ChatClient chatClient = chatClientBuilder.build();

            // Agent 循环
            for (int stepNo = 1; stepNo <= MAX_STEPS; stepNo++) {
                AgentDecision decision = decideNextAction(chatClient, request, toolResults, stepNo);
                // 模型判断信息足够或遇到重复工具调用后，统一进入最终答案生成，不再继续循环。
                if ("final".equals(decision.action())) {
                    TravelAgentStep step = finalStep(stepNo, decision);
                    steps.add(step);
                    if (!safeSend(emitter, "step", toJsonSafe(step))) {
                        return;
                    }
                    break;
                }

                TravelToolCall call = buildToolCall(stepNo, decision);
                String callKey = canonicalToolCallKey(call);
                if (callKeys.contains(callKey)) {
                    TravelAgentStep step = duplicateStep(stepNo, call);
                    steps.add(step);
                    if (!safeSend(emitter, "step", toJsonSafe(step))) {
                        return;
                    }
                    break;
                }

                callKeys.add(callKey);
                TravelToolResult result = executeTool(call);
                toolResults.add(result);

                TravelAgentStep step = toolStep(stepNo, decision, call, result);
                steps.add(step);
                if (!safeSend(emitter, "step", toJsonSafe(step))) {
                    return;
                }
                if (!safeSend(emitter, "tool", toJsonSafe(result))) {
                    return;
                }
            }

            streamFinalAnswer(chatClient, request, toolResults, emitter);
            if (!safeSend(emitter, "refs", toJsonSafe(collectReferences(toolResults)))) {
                return;
            }
            if (!safeSend(emitter, "done", "[DONE]")) {
                return;
            }
            safeComplete(emitter);
        } catch (Exception e) {
            log.warn("Agent流式对话异常: {}", e.getMessage());
            safeSend(emitter, "error", fallbackAnswer(request.getMessage()));
            safeComplete(emitter);
        }
    }

    /**
     * 执行完整 Agent 循环。
     * 每轮先让模型或启发式规则选择下一步工具，再执行工具并把观察结果写回上下文；
     * 达到最大步数、模型判断信息足够或遇到重复工具调用后，统一进入最终答案生成。
     *
     * @param request 用户问题和最近历史对话
     * @return 包含最终答案、引用、步骤轨迹和工具调用记录的 Agent 执行结果
     */
    public AgentRun run(TravelChatRequest request) {
        ChatClient chatClient = chatClientBuilder.build();
        List<TravelAgentStep> steps = new ArrayList<>();
        List<TravelToolCall> toolCalls = new ArrayList<>();
        List<TravelToolResult> toolResults = new ArrayList<>();
        Set<String> callKeys = new LinkedHashSet<>();

        for (int stepNo = 1; stepNo <= MAX_STEPS; stepNo++) {
            AgentDecision decision = decideNextAction(chatClient, request, toolResults, stepNo);
            if ("final".equals(decision.action())) {
                steps.add(finalStep(stepNo, decision));
                break;
            }

            TravelToolCall call = buildToolCall(stepNo, decision);
            String callKey = canonicalToolCallKey(call);
            if (callKeys.contains(callKey)) {
                // 同一工具和同一参数重复调用通常不会增加信息量，直接结束工具循环并进入最终生成。
                steps.add(duplicateStep(stepNo, call));
                break;
            }

            callKeys.add(callKey);
            toolCalls.add(call);
            TravelToolResult result = executeTool(call);
            toolResults.add(result);

            steps.add(toolStep(stepNo, decision, call, result));
        }

        String answer = generateFinalAnswer(chatClient, request, toolResults);
        List<TravelChatResponse.TravelNoteReference> references = collectReferences(toolResults);
        return new AgentRun(answer, references, steps, toolCalls, toolResults);
    }

    private AgentDecision decideNextAction(ChatClient chatClient, TravelChatRequest request, List<TravelToolResult> toolResults, int stepNo) {
        if (stepNo == 1) {
            // 第一轮用轻量启发式兜底，保证天气/价格/攻略类问题能稳定进入正确工具。
            AgentDecision heuristic = heuristicFirstAction(request);
            if (heuristic != null) {
                return heuristic;
            }
        }

        try {
            String raw = chatClient.prompt()
                    .system(agentSystemPrompt())
                    .user(composeDecisionPrompt(request, toolResults))
                    .call()
                    .content();
            AgentDecision decision = parseDecision(raw);
            if (decision != null) {
                return decision;
            }
        } catch (Exception e) {
            log.warn("旅行Agent动作决策失败: {}", e.getMessage());
        }
        return fallbackDecision(request, toolResults);
    }

    private TravelToolCall buildToolCall(int stepNo, AgentDecision decision) {
        TravelToolCall call = new TravelToolCall();
        call.setStep(stepNo);
        call.setToolName(decision.toolName());
        call.setArguments(decision.arguments() == null ? Collections.emptyMap() : decision.arguments());
        call.setReason(decision.thought());
        call.setStatus("running");
        return call;
    }

    // final 步骤不再执行工具，专门用于模型判断信息足够时的输出，避免模型在决策不明确时继续循环产生无效工具调用。
    private TravelAgentStep finalStep(int stepNo, AgentDecision decision) {
        TravelAgentStep step = new TravelAgentStep();
        step.setStep(stepNo);
        step.setAction("final");
        step.setThought(decision.thought());
        return step;
    }

    // 遇到重复工具调用时的特殊步骤，标明被跳过的工具和原因，便于前端展示和调试分析。
    private TravelAgentStep duplicateStep(int stepNo, TravelToolCall call) {
        TravelAgentStep step = new TravelAgentStep();
        step.setStep(stepNo);
        step.setAction("skip_duplicate");
        step.setThought("跳过重复工具调用: " + call.getToolName());
        step.setToolCall(call);
        return step;
    }

    // 普通工具调用步骤，包含模型决策的思路、工具调用参数和工具执行结果。
    private TravelAgentStep toolStep(int stepNo, AgentDecision decision, TravelToolCall call, TravelToolResult result) {
        TravelAgentStep step = new TravelAgentStep();
        step.setStep(stepNo);
        step.setAction("tool");
        step.setThought(decision.thought());
        step.setToolCall(call);
        step.setToolResult(result);
        return step;
    }

    private TravelToolResult executeTool(TravelToolCall call) {
        long started = System.currentTimeMillis();
        TravelToolResult result = new TravelToolResult();
        result.setStep(call.getStep());
        result.setToolName(call.getToolName());
        result.setArguments(call.getArguments());

        // 工具调用放到独立线程，便于用 Future 控制 3 秒超时，避免外部服务或向量检索阻塞 Agent。
        Future<Object> future;
        try {
            future = toolExecutor.submit((Callable<Object>) () -> invokeTool(call));
        } catch (RejectedExecutionException e) {
            markToolFailed(call, result, "工具线程池繁忙");
            result.setElapsedMs(System.currentTimeMillis() - started);
            return result;
        }

        try {
            Object payload = future.get(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            result.setSuccess(true);
            result.setContent(toJsonSafe(payload));
            if (payload instanceof CommunitySearchResult communitySearchResult) {
                result.setReferences(communitySearchResult.getReferences());
            }
            call.setStatus("success");
        } catch (TimeoutException e) {
            future.cancel(true);
            markToolFailed(call, result, "工具调用超时");
        } catch (Exception e) {
            future.cancel(true);
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            markToolFailed(call, result, message);
        } finally {
            result.setElapsedMs(System.currentTimeMillis() - started);
        }
        return result;
    }

    private void markToolFailed(TravelToolCall call, TravelToolResult result, String reason) {
        String message = reason == null || reason.isBlank() ? "未知错误" : reason;
        result.setSuccess(false);
        result.setError(message);
        result.setContent("工具调用失败: " + message);
        call.setStatus("failed");
    }

    private Object invokeTool(TravelToolCall call) {
        Map<String, Object> args = call.getArguments() == null ? Collections.emptyMap() : call.getArguments();
        String toolName = call.getToolName();
        if ("search_community_notes".equals(toolName)) {
            return travelAgentTools.searchCommunityNotes(
                    str(args.get("query")),
                    str(args.get("destination")),
                    stringList(args.get("interests")),
                    integer(args.get("topK")));
        }
        if ("get_weather_forecast".equals(toolName)) {
            return travelAgentTools.getWeatherForecast(
                    str(args.get("destination")),
                    str(args.get("startDate")),
                    integer(args.get("days")));
        }
        if ("search_travel_prices".equals(toolName)) {
            return travelAgentTools.searchTravelPrices(
                    str(args.get("destination")),
                    str(args.get("origin")),
                    str(args.get("startDate")),
                    integer(args.get("days")),
                    str(args.get("travelers")),
                    str(args.get("budgetLevel")));
        }
        if ("estimate_trip_budget".equals(toolName)) {
            return travelAgentTools.estimateTripBudget(
                    str(args.get("destination")),
                    integer(args.get("days")),
                    str(args.get("travelers")),
                    str(args.get("travelStyle")));
        }
        throw new IllegalArgumentException("未知工具: " + toolName);
    }

    /**
     * 基于全部工具观察结果生成最终攻略。
     * <p>
     * 注意最终生成不再调用工具，只负责把已有观察整合成用户可读的 Markdown。
     */
    private String generateFinalAnswer(ChatClient chatClient, TravelChatRequest request, List<TravelToolResult> toolResults) {
        try {
            String answer = chatClient.prompt()
                    .system(finalSystemPrompt())
                    .user(composeFinalPrompt(request, toolResults))
                    .call()
                    .content();
            if (answer != null && !answer.isBlank()) {
                return answer;
            }
        } catch (Exception e) {
            log.warn("旅行Agent最终生成失败: {}", e.getMessage());
        }
        return "暂时无法使用大模型生成，已回退到基础建议。你可以补充目的地、天数、预算，我会继续生成更精准攻略。\n需求: " + request.getMessage();
    }

    /**
     * 流式生成最终答案，逐 token 推送 `chunk` 事件。
     * <p>
     * 如果客户端中途断开，`takeWhile` 会停止消费模型流，避免继续向已关闭的 SSE 连接写数据。
     */
    private void streamFinalAnswer(ChatClient chatClient,
                                   TravelChatRequest request,
                                   List<TravelToolResult> toolResults,
                                   SseEmitter emitter) {
        chatClient.prompt()
                .system(finalSystemPrompt())
                .user(composeFinalPrompt(request, toolResults))
                .stream()
                .content()
                .takeWhile(token -> safeSend(emitter, "chunk", token))
                .blockLast();
    }

    private AgentDecision heuristicFirstAction(TravelChatRequest request) {
        String message = request.getMessage() == null ? "" : request.getMessage();
        Map<String, Object> args = baseArgs(request);
        if (message.matches(".*(天气|温度|下雨|降雨|穿什么|穿衣|冷不冷|热不热).*")) {
            return new AgentDecision("tool", "get_weather_forecast", args, "用户问题优先涉及天气或穿衣。");
        }
        if (message.matches(".*(预算|价格|花费|多少钱|机票|高铁|酒店|门票|人均).*")) {
            return new AgentDecision("tool", "search_travel_prices", args, "用户问题优先涉及价格或预算。");
        }
        if (message.matches(".*(攻略|路线|景点|美食|避坑|小众|拍照|怎么玩|行程).*")) {
            return new AgentDecision("tool", "search_community_notes", args, "攻略类问题优先检索社区笔记。");
        }
        return null;
    }

    private AgentDecision fallbackDecision(TravelChatRequest request, List<TravelToolResult> toolResults) {
        String message = request.getMessage() == null ? "" : request.getMessage();
        List<String> desiredTools = desiredTools(message);
        if (desiredTools.isEmpty()) {
            desiredTools = List.of("search_community_notes");
        }
        for (String toolName : desiredTools) {
            if (!hasToolResult(toolResults, toolName)) {
                // 当模型没有输出合法决策时，按需求关键词顺序补齐尚未调用过的必要工具。
                return new AgentDecision("tool", toolName, baseArgs(request), "根据用户需求继续补充工具结果。");
            }
        }
        return new AgentDecision("final", null, Collections.emptyMap(), "已有工具结果，生成最终答案。");
    }

    private List<String> desiredTools(String message) {
        List<String> tools = new ArrayList<>();
        if (message.matches(".*(天气|温度|下雨|降雨|穿什么|穿衣|冷不冷|热不热|明天|后天|出发日期).*")) {
            tools.add("get_weather_forecast");
        }
        if (message.matches(".*(预算|价格|花费|多少钱|机票|高铁|酒店|门票|人均|省钱).*")) {
            tools.add("search_travel_prices");
        }
        if (message.matches(".*(攻略|路线|景点|美食|避坑|小众|拍照|怎么玩|行程|旅行|旅游).*")) {
            tools.add("search_community_notes");
        }
        return tools;
    }

    private boolean hasToolResult(List<TravelToolResult> toolResults, String toolName) {
        return toolResults.stream().anyMatch(result -> toolName.equals(result.getToolName()));
    }

    private Map<String, Object> baseArgs(TravelChatRequest request) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("query", request.getMessage());
        // 第一版目的地和天数从自然语言粗略抽取，后续可替换为结构化槽位识别。
        args.put("destination", inferDestination(request.getMessage()));
        args.put("topK", request.getTopK() == null ? 5 : request.getTopK());
        args.put("days", inferDays(request.getMessage()));
        args.put("budgetLevel", request.getMessage());
        return args;
    }

    private AgentDecision parseDecision(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = extractJsonObject(raw);
        try {
            Map<String, Object> map = objectMapper.readValue(text, new TypeReference<>() {
            });
            String action = str(map.get("action"));
            String toolName = str(map.get("tool_name"));
            if (toolName == null) {
                toolName = str(map.get("toolName"));
            }
            Map<String, Object> arguments = objectMap(map.get("arguments"));
            String thought = str(map.get("thought"));
            if ("final".equals(action)) {
                return new AgentDecision("final", null, Collections.emptyMap(), thought);
            }
            if ("tool".equals(action) && isKnownTool(toolName)) {
                return new AgentDecision("tool", toolName, arguments, thought);
            }
        } catch (Exception e) {
            log.warn("解析Agent决策JSON失败: {}", e.getMessage());
        }
        return null;
    }

    private boolean isKnownTool(String toolName) {
        return "search_community_notes".equals(toolName)
                || "get_weather_forecast".equals(toolName)
                || "search_travel_prices".equals(toolName)
                || "estimate_trip_budget".equals(toolName);
    }

    /**
     * 决策提示词要求模型只返回 JSON，后端据此选择工具或进入最终答案。
     */
    private String composeDecisionPrompt(TravelChatRequest request, List<TravelToolResult> toolResults) {
        return "用户问题:\n" + request.getMessage() + "\n\n"
                + "最近历史对话:\n" + renderHistory(request.getHistory()) + "\n\n"
                + "当前已获得的工具结果:\n" + toJsonSafe(toolResults) + "\n\n"
                + "请判断下一步最有价值的动作。只输出 JSON，不要 Markdown。格式为：\n"
                + "{\"action\":\"tool\",\"tool_name\":\"search_community_notes\",\"thought\":\"原因\",\"arguments\":{...}}\n"
                + "或 {\"action\":\"final\",\"thought\":\"信息足够\"}。";
    }

    /**
     * 最终答案提示词固定输出结构，并要求明确区分社区笔记、工具结果和通用经验。
     */
    private String composeFinalPrompt(TravelChatRequest request, List<TravelToolResult> toolResults) {
        return "请基于以下信息生成最终旅行攻略：\n\n"
                + "用户需求:\n" + request.getMessage() + "\n\n"
                + "工具查询结果:\n" + toJsonSafe(toolResults) + "\n\n"
                + "历史对话:\n" + renderHistory(request.getHistory()) + "\n\n"
                + "输出要求:\n"
                + "1. 不要输出工具调用过程。\n"
                + "2. 明确哪些建议来自社区笔记，哪些来自天气/价格工具，哪些是通用经验补充。\n"
                + "3. 如果实时工具失败或信息不足，必须说明不确定性。\n"
                + "4. 使用中文 Markdown。\n"
                + "5. 结构固定为：行程规划、预算建议、避坑提醒、可选替代方案。";
    }

    private String renderHistory(List<TravelChatRequest.ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "无";
        }
        StringBuilder builder = new StringBuilder();
        int start = Math.max(0, history.size() - MAX_HISTORY);
        for (int i = start; i < history.size(); i++) {
            TravelChatRequest.ChatMessage msg = history.get(i);
            if (msg == null || msg.getContent() == null || msg.getContent().isBlank()) {
                continue;
            }
            String role = "assistant".equalsIgnoreCase(msg.getRole()) ? "助手" : "用户";
            builder.append(role).append(": ").append(msg.getContent()).append("\n");
        }
        return builder.isEmpty() ? "无" : builder.toString();
    }

    private String agentSystemPrompt() {
        return "你是小旅书旅行攻略 Agent，目标是为用户生成可靠、可执行的旅行攻略。"
                + "你可以使用工具：search_community_notes、get_weather_forecast、search_travel_prices、estimate_trip_budget。"
                + "不要编造实时天气、价格、营业状态等信息；需要时调用工具。"
                + "RAG 检索也是工具，不是固定前置步骤。攻略类问题优先检索社区笔记。"
                + "不要重复调用相同工具和相同参数。信息足够时输出 final。";
    }

    private String finalSystemPrompt() {
        return "你是小旅书旅行攻略 Agent，最终回答必须综合工具结果并标明依据来源。"
                + "最终回答使用中文 Markdown，结构包含：行程规划、预算建议、避坑提醒、可选替代方案。";
    }

    private List<TravelChatResponse.TravelNoteReference> collectReferences(List<TravelToolResult> toolResults) {
        Map<Long, TravelChatResponse.TravelNoteReference> refs = new LinkedHashMap<>();
        for (TravelToolResult result : toolResults) {
            if (result.getReferences() == null) {
                continue;
            }
            for (TravelChatResponse.TravelNoteReference ref : result.getReferences()) {
                refs.putIfAbsent(ref.getPostId(), ref);
            }
        }
        return new ArrayList<>(refs.values());
    }

    private String extractJsonObject(String raw) {
        String text = raw.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String inferDestination(String message) {
        if (message == null) {
            return null;
        }
        String[] suffixes = {"怎么玩", "攻略", "路线", "天气", "穿什么", "预算", "三天", "两天", "2天", "3天"};
        String text = message.replaceAll("[，。！？?]", " ");
        for (String suffix : suffixes) {
            int index = text.indexOf(suffix);
            if (index > 0) {
                String candidate = text.substring(Math.max(0, index - 8), index).trim();
                if (!candidate.isBlank()) {
                    return candidate.replaceAll(".*去", "").replaceAll(".*到", "").trim();
                }
            }
        }
        return null;
    }

    private Integer inferDays(String message) {
        if (message == null) {
            return 3;
        }
        if (message.contains("一天") || message.contains("1天")) {
            return 1;
        }
        if (message.contains("两天") || message.contains("2天")) {
            return 2;
        }
        if (message.contains("三天") || message.contains("3天")) {
            return 3;
        }
        if (message.contains("四天") || message.contains("4天")) {
            return 4;
        }
        return 3;
    }

    private String str(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private Integer integer(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(Object::toString).toList();
        }
        if (value == null || value.toString().isBlank()) {
            return Collections.emptyList();
        }
        return List.of(value.toString().split("[,，、]"));
    }

    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return result;
        }
        return Collections.emptyMap();
    }

    private String toJsonSafe(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    /**
     * 生成稳定的工具调用去重 key。
     * <p>
     * 参数会先去空、去首尾空格并按 key 排序，避免同一语义参数因为 Map 顺序不同而重复调用。
     */
    private String canonicalToolCallKey(TravelToolCall call) {
        return call.getToolName() + ":" + toJsonSafe(normalizeArguments(call.getArguments()));
    }

    private Map<String, Object> normalizeArguments(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> sorted = new TreeMap<>();
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            Object value = normalizeValue(entry.getValue());
            if (value instanceof String text && text.isBlank()) {
                continue;
            }
            sorted.put(entry.getKey(), value);
        }
        return sorted;
    }

    private Object normalizeValue(Object value) {
        if (value instanceof String text) {
            return text.trim();
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(this::normalizeValue)
                    .toList();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    nested.put(entry.getKey().toString(), normalizeValue(entry.getValue()));
                }
            }
            return nested;
        }
        return value;
    }

    /**
     * 安全发送 SSE 事件。客户端断开时不再抛出运行时异常，避免 catch 中二次发送 error 造成噪声。
     */
    private boolean safeSend(SseEmitter emitter, String eventName, String data) {
        try {
            String safeEventName = Objects.requireNonNull(eventName == null ? "message" : eventName);
            Object safeData = Objects.requireNonNull(data == null ? "" : data);
            emitter.send(SseEmitter.event().name(safeEventName).data(safeData));
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE发送失败，客户端可能已断开: {}", e.getMessage());
            try {
                emitter.completeWithError(e);
            } catch (Exception ignored) {
                // emitter 可能已经完成。
            }
            return false;
        }
    }

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // emitter 可能已经完成。
        }
    }

    private String fallbackAnswer(String userPrompt) {
        return "暂时无法使用大模型生成，已回退到基础建议。你可以补充目的地、天数、预算，我会继续生成更精准攻略。\n需求: " + userPrompt;
    }

    private record AgentDecision(String action, String toolName, Map<String, Object> arguments, String thought) {
    }

    public record AgentRun(
            String answer,
            List<TravelChatResponse.TravelNoteReference> references,
            List<TravelAgentStep> steps,
            List<TravelToolCall> toolCalls,
            List<TravelToolResult> toolResults) {
    }
}
