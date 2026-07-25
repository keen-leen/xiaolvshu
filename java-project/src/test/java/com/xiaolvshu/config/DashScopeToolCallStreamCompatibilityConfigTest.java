package com.xiaolvshu.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DashScopeToolCallStreamCompatibilityConfigTest {

    @Test
    void shouldTreatEmptyToolCallIdAsContinuationChunk() {
        String line = """
                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"","function":{"arguments":"杭州\\"}","name":null},"type":"function"}]}}]}""";

        assertEquals("""
                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":null,"function":{"arguments":"杭州\\"}","name":null},"type":"function"}]}}]}""",
                DashScopeToolCallStreamCompatibilityConfig.normalizeSseLine(line));
    }

    @Test
    void shouldKeepFirstToolCallChunkAndDoneMarkerUnchanged() {
        String firstChunk = """
                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_123","function":{"arguments":"{\\"query\\":","name":"searchCommunityNotes"},"type":"function"}]}}]}""";

        assertEquals(firstChunk,
                DashScopeToolCallStreamCompatibilityConfig.normalizeSseLine(firstChunk));
        assertEquals("data: [DONE]",
                DashScopeToolCallStreamCompatibilityConfig.normalizeSseLine("data: [DONE]"));
    }

    @Test
    void shouldNotRewriteEscapedJsonInsideNaturalLanguageContent() {
        String contentChunk = """
                data: {"choices":[{"delta":{"content":"示例：{\\"id\\":\\"\\"}"}}]}""";

        assertEquals(contentChunk,
                DashScopeToolCallStreamCompatibilityConfig.normalizeSseLine(contentChunk));
    }
}
