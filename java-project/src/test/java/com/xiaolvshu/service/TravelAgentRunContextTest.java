package com.xiaolvshu.service;

import com.xiaolvshu.dto.CommunitySearchResult;
import com.xiaolvshu.dto.TravelNoteReference;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelAgentRunContextTest {

    @Test
    void shouldClampTopKAndLimitToolCalls() {
        List<String> statuses = new ArrayList<>();
        TravelAgentRunContext context = new TravelAgentRunContext(
                5, 3, status -> statuses.add(status.code()));

        assertEquals(5, context.prepareCommunitySearch(null));
        assertEquals(2, context.prepareCommunitySearch(2));
        assertEquals(5, context.prepareCommunitySearch(10));
        assertThrows(IllegalStateException.class, () -> context.prepareCommunitySearch(1));
        assertEquals(List.of("searching", "searching", "searching"), statuses);
    }

    @Test
    void shouldKeepSourceIdsStableAcrossMultipleSearches() {
        TravelAgentRunContext context = new TravelAgentRunContext(5, 3, ignored -> { });

        String firstText = context.registerSearchResult(result(
                "西湖路线来自[S1]",
                reference(101L, "西湖一日游")));
        String secondText = context.registerSearchResult(result(
                "重复笔记[S1]，新增笔记[S2]",
                reference(101L, "重复标题"),
                reference(202L, "灵隐寺避坑")));

        assertTrue(firstText.contains("西湖路线来自[S1]"));
        assertTrue(secondText.contains("重复笔记[S1]，新增笔记[S2]"));
        assertEquals(2, context.references().size());
        assertEquals("S1", context.references().get(0).getSourceId());
        assertEquals("西湖一日游", context.references().get(0).getTitle());
        assertEquals("S2", context.references().get(1).getSourceId());
    }

    @Test
    void shouldRejectUnknownLocalReference() {
        TravelAgentRunContext context = new TravelAgentRunContext(5, 3, ignored -> { });

        String text = context.registerSearchResult(result(
                "模型上下文错误地引用了[S2]",
                reference(101L, "只有一条")));

        assertTrue(text.contains("[来源不可用]"));
    }

    private CommunitySearchResult result(String contextText, TravelNoteReference... references) {
        CommunitySearchResult result = new CommunitySearchResult();
        result.setContextText(contextText);
        result.setReferences(List.of(references));
        return result;
    }

    private TravelNoteReference reference(Long postId, String title) {
        TravelNoteReference reference = new TravelNoteReference();
        reference.setPostId(postId);
        reference.setTitle(title);
        reference.setTags(List.of("杭州"));
        return reference;
    }
}
