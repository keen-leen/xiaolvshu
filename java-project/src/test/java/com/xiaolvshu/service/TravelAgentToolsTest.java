package com.xiaolvshu.service;

import com.xiaolvshu.dto.CommunitySearchResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

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
        TravelAgentTools tools = new TravelAgentTools(ragService);
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
}
