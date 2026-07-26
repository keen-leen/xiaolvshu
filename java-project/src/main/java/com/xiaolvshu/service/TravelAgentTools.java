package com.xiaolvshu.service;

import com.xiaolvshu.dto.CommunitySearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TravelAgentTools {

    private final RagService ragService;
    private final WeatherService weatherService;

    /**
     * 社区笔记检索工具。
     *
     * RAG 在 Agent 中被当作普通工具使用，而不是固定前置步骤；攻略、路线、避坑等问题通常会优先调用它。
     * Spring AI 2.0 会从 @Tool/@ToolParam 生成模型可见的 JSON Schema；真正执行前再由
     * 服务端 RunContext 限制 topK 和调用次数，不能把模型传入的参数直接视为可信输入。
     */
    @Tool(name = "search_community_notes", description = """
            检索小旅书社区中的真实旅行笔记。规划目的地路线、景点、美食、亲子玩法、
            避坑建议或小众体验时应优先使用；纯寒暄、改写和不需要社区事实的问题不应调用。
            """)
    public String searchCommunityNotes(
            @ToolParam(description = "用户查询文本") String query,
            @ToolParam(description = "目的地，可为空", required = false) String destination,
            @ToolParam(description = "兴趣偏好列表，可为空", required = false) List<String> interests,
            @ToolParam(description = "期望返回条数，1到10，可为空", required = false) Integer topK,
            ToolContext toolContext) {
        // ToolContext 中保存的是服务端创建的请求上下文，模型无法修改其中的状态回调和调用上限。
        TravelAgentRunContext runContext = requireRunContext(toolContext);
        int safeTopK = runContext.prepareCommunitySearch(topK);
        CommunitySearchResult result = ragService.searchCommunityNotes(
                normalizeRequired(query, 500, "检索词"),
                normalizeOptional(destination, 100),
                normalizeInterests(interests),
                safeTopK);
        return runContext.registerSearchResult(result);
    }

    /**
     * 实时天气和未来 7 日预报工具。
     *
     * 工具层只声明模型何时调用以及参数含义，HTTP 请求和结果整理全部由 WeatherService 负责。
     * 以后调整天气供应商或返回格式时，不需要修改 Agent 配置和工具协议。
     */
    @Tool(name = "get_weather", description = """
            查询指定地点的实时天气和未来7日天气预报。用户询问当前天气、近期温度、
            降雨、穿衣建议，或需要结合天气安排行程时使用。不能查询7日以后的天气。
            """)
    public String getWeather(
            @ToolParam(description = "地点名称，例如杭州、北京朝阳区") String location,
            ToolContext toolContext) {
        TravelAgentRunContext runContext = requireRunContext(toolContext);
        String safeLocation = normalizeRequired(location, 100, "地点");

        // 与社区检索共用 status 事件，前端无需增加天气专用协议或解析逻辑。
        runContext.reportStatus(
                "weather_searching",
                "正在查询" + safeLocation + "的当前及未来7日天气");
        try {
            return weatherService.getWeather(safeLocation);
        } finally {
            // 无论供应商返回成功还是受控失败文本，都提示模型结果已经进入回答整理阶段。
            runContext.reportStatus("writing", "正在结合天气信息整理回答");
        }
    }

    /**
     * @Tool 的 JSON Schema 能帮助模型生成正确形状，但模型输出仍属于外部输入。
     * 这里集中限制字符串和数组大小，避免异常模型响应把超长文本继续送入 Embedding 服务。
     */
    private String normalizeRequired(String value, int maxLength, String fieldName) {
        String normalized = normalizeOptional(value, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return normalized;
    }

    // 从 Spring AI 的 ToolContext 中读取当前请求上下文，避免每个工具重复编写类型检查。
    private TravelAgentRunContext requireRunContext(ToolContext toolContext) {
        Object value = toolContext.getContext().get(TravelAgentRunContext.TOOL_CONTEXT_KEY);
        if (value instanceof TravelAgentRunContext runContext) {
            return runContext;
        }
        throw new IllegalStateException("旅行Agent运行上下文缺失");
    }

    // normalizeOptional 允许 null 或空白字符串，返回 null；否则去除首尾空白并截断到 maxLength。
    private String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength? normalized : normalized.substring(0, maxLength);
    }

    // normalizeInterests 允许 null 或空列表，返回空列表；否则去除首尾空白、截断到 maxLength，并限制最多 10 个兴趣。
    private List<String> normalizeInterests(List<String> interests) {
        if (interests == null || interests.isEmpty()) {
            return Collections.emptyList();
        }
        return interests.stream()
                .limit(10)
                .map(value -> normalizeOptional(value, 50))
                .filter(value -> value != null)
                .toList();
    }
}
