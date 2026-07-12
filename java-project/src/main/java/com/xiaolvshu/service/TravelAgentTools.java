package com.xiaolvshu.service;

import com.xiaolvshu.dto.CommunitySearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TravelAgentTools {

    private final RagService travelRagService;

    /**
     * 社区笔记检索工具。
     * <p>
     * RAG 在 Agent 中被当作普通工具使用，而不是固定前置步骤；攻略、路线、避坑等问题通常会优先调用它。
     * 当前 Agent 仍由后端手动执行工具，@Tool 主要作为工具 schema 元信息和后续接入 Spring AI 原生 tool-calling 的准备。
     */
    @Tool(name = "search_community_notes", description = "检索小旅书社区真实旅行笔记，适合攻略、路线、景点、美食、避坑、小众玩法等问题。")
    public CommunitySearchResult searchCommunityNotes(
            @ToolParam(description = "用户查询文本") String query,
            @ToolParam(description = "目的地，可为空", required = false) String destination,
            @ToolParam(description = "兴趣偏好列表，可为空", required = false) List<String> interests,
            @ToolParam(description = "返回条数，1到10", required = false) Integer topK) {
        return travelRagService.searchCommunityNotes(query, destination, interests, topK);
    }

    /**
     * 天气查询工具。
     * <p>
     * 当前实现是 mock provider，返回结构保持稳定，后续接入真实天气 API 时只需要替换内部数据源。
     */
    @Tool(name = "get_weather_forecast", description = "查询目的地日期范围内天气。第一版为本地 mock/provider 适配层，输出不代表实时天气。")
    public Map<String, Object> getWeatherForecast(
            @ToolParam(description = "目的地") String destination,
            @ToolParam(description = "开始日期，格式 yyyy-MM-dd，可为空", required = false) String startDate,
            @ToolParam(description = "天数，1到10", required = false) Integer days) {
        int safeDays = days == null ? 3 : Math.max(1, Math.min(10, days));
        LocalDate start = parseDate(startDate);
        List<Map<String, Object>> forecasts = new ArrayList<>();
        for (int i = 0; i < safeDays; i++) {
            // 第一版用可预测的规则数据模拟天气，避免模型直接编造实时天气。
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", start.plusDays(i).toString());
            item.put("weather", i % 3 == 1 ? "多云" : "晴到多云");
            item.put("temperature", (18 + i % 4) + "-" + (27 + i % 3) + "℃");
            item.put("rain", i % 4 == 2 ? "有小概率阵雨" : "降雨概率低");
            forecasts.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("destination", destination);
        result.put("provider", "mock-weather-provider");
        result.put("sourceNote", "当前为规则模拟天气，接入真实天气 API 后可替换 provider。");
        result.put("forecast", forecasts);
        result.put("advice", "建议出行前再次确认实时天气；户外行程预留室内备选，随身带轻便雨具。");
        return result;
    }

    /**
     * 旅行价格估算工具。
     * <p>
     * 第一版使用规则估算住宿、餐饮、市内交通和门票体验；实时机票/高铁/酒店价格后续由真实 provider 补充。
     */
    @Tool(name = "search_travel_prices", description = "查询或估算交通、住宿、门票等价格。第一版使用规则估算。")
    public Map<String, Object> searchTravelPrices(
            @ToolParam(description = "目的地") String destination,
            @ToolParam(description = "出发地，可为空", required = false) String origin,
            @ToolParam(description = "开始日期，格式 yyyy-MM-dd，可为空", required = false) String startDate,
            @ToolParam(description = "天数", required = false) Integer days,
            @ToolParam(description = "旅行人数或同行人描述，可为空", required = false) String travelers,
            @ToolParam(description = "预算档位，如 省钱/舒适/高端，可为空", required = false) String budgetLevel) {
        int safeDays = days == null ? 3 : Math.max(1, days);
        int people = inferPeople(travelers);
        // 根据预算档位选择单价基准，保持返回的预算拆分可解释。
        int hotelPerNight = priceByLevel(budgetLevel, 220, 420, 800);
        int mealPerDay = priceByLevel(budgetLevel, 90, 160, 280);
        int localTransportPerDay = priceByLevel(budgetLevel, 35, 80, 180);
        int ticketsPerDay = priceByLevel(budgetLevel, 60, 120, 240);

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(priceItem("住宿", hotelPerNight * Math.max(1, safeDays - 1), "按每晚" + hotelPerNight + "元估算"));
        items.add(priceItem("餐饮", mealPerDay * safeDays * people, "按每人每天" + mealPerDay + "元估算"));
        items.add(priceItem("市内交通", localTransportPerDay * safeDays * people, "按每人每天" + localTransportPerDay + "元估算"));
        items.add(priceItem("门票体验", ticketsPerDay * safeDays * people, "按每人每天" + ticketsPerDay + "元估算"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("destination", destination);
        result.put("origin", origin);
        result.put("startDate", startDate);
        result.put("days", safeDays);
        result.put("travelers", travelers);
        result.put("budgetLevel", budgetLevel);
        result.put("provider", "rule-price-estimator");
        result.put("sourceNote", "价格为规则估算区间，不含实时机票/高铁波动。");
        result.put("items", items);
        result.put("range", sum(items) + "-" + Math.round(sum(items) * 1.25) + "元");
        return result;
    }

    /**
     * 预算拆分工具。
     * <p>
     * 当价格工具信息不足或用户只要求预算建议时，复用规则价格估算生成基础拆分。
     */
    @Tool(name = "estimate_trip_budget", description = "在实时价格不足时生成预算拆分。")
    public Map<String, Object> estimateTripBudget(
            @ToolParam(description = "目的地") String destination,
            @ToolParam(description = "天数") Integer days,
            @ToolParam(description = "旅行人数或同行人描述，可为空", required = false) String travelers,
            @ToolParam(description = "旅行风格，如 省钱高效/轻松休闲/高端，可为空", required = false) String travelStyle) {
        return searchTravelPrices(destination, null, null, days, travelers, travelStyle);
    }

    private LocalDate parseDate(String startDate) {
        try {
            if (startDate != null && !startDate.isBlank()) {
                return LocalDate.parse(startDate.trim());
            }
        } catch (Exception ignored) {
            // fallback to current date
        }
        return LocalDate.now();
    }

    private int inferPeople(String travelers) {
        if (travelers == null || travelers.isBlank()) {
            return 1;
        }
        if (travelers.contains("亲子") || travelers.contains("家庭")) {
            return 3;
        }
        if (travelers.contains("情侣") || travelers.contains("两") || travelers.contains("2")) {
            return 2;
        }
        return 1;
    }

    private int priceByLevel(String level, int low, int mid, int high) {
        String text = level == null ? "" : level;
        if (text.contains("省") || text.contains("低") || text.contains("穷游")) {
            return low;
        }
        if (text.contains("高") || text.contains("奢") || text.contains("舒适")) {
            return high;
        }
        return mid;
    }

    private Map<String, Object> priceItem(String name, int amount, String note) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("amount", amount);
        item.put("note", note);
        return item;
    }

    private int sum(List<Map<String, Object>> items) {
        return items.stream()
                .map(item -> item.get("amount"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .mapToInt(Number::intValue)
                .sum();
    }
}
