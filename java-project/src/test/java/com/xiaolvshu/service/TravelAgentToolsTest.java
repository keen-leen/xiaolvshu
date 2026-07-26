package com.xiaolvshu.service;

import com.xiaolvshu.dto.CommunitySearchResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TravelAgentToolsTest {

    @Test
    void shouldPassBoundedArgumentsAndReturnNormalizedContext() {
        RagService ragService = mock(RagService.class);
        WeatherService weatherService = mock(WeatherService.class);
        TravelAgentTools tools = new TravelAgentTools(ragService, weatherService);
        TravelAgentRunContext runContext = new TravelAgentRunContext(5, 3, ignored -> { });
        CommunitySearchResult result = new CommunitySearchResult();
        result.setContextText("未检索到可靠社区笔记");
        result.setReferences(List.of());
        when(ragService.searchCommunityNotes("杭州亲子", "杭州", List.of("亲子"), 5))
                .thenReturn(result);

        String context = tools.searchCommunityNotes(
                "杭州亲子",
                "杭州",
                List.of("亲子"),
                10,
                new ToolContext(Map.of(TravelAgentRunContext.TOOL_CONTEXT_KEY, runContext)));

        assertTrue(context.contains("未检索到可靠社区笔记"));
        verify(ragService).searchCommunityNotes("杭州亲子", "杭州", List.of("亲子"), 5);
    }

    @Test
    void shouldDelegateWeatherQueryToWeatherService() {
        RagService ragService = mock(RagService.class);
        WeatherService weatherService = mock(WeatherService.class);
        TravelAgentTools tools = new TravelAgentTools(ragService, weatherService);
        List<String> statuses = new ArrayList<>();
        TravelAgentRunContext runContext = new TravelAgentRunContext(
                5, 3, status -> statuses.add(status.code() + ":" + status.message()));
        when(weatherService.getWeather("杭州")).thenReturn("杭州今天晴，32℃");

        String result = tools.getWeather(
                "杭州",
                new ToolContext(Map.of(TravelAgentRunContext.TOOL_CONTEXT_KEY, runContext)));

        assertTrue(result.contains("杭州今天晴"));
        assertTrue(statuses.contains("weather_searching:正在查询杭州的当前及未来7日天气"));
        assertTrue(statuses.contains("writing:正在结合天气信息整理回答"));
        verify(weatherService).getWeather("杭州");
    }
}
