package com.xiaolvshu.service;

import com.xiaolvshu.entity.Post;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RagServiceTest {

    private final RagService ragService = new RagService(null, null, null, null, null);

    @Test
    @SuppressWarnings("unchecked")
    void shouldStoreTagsAsKeywordArrayInChunkMetadata() {
        Post post = new Post();
        post.setId(1L);
        post.setTitle("成都美食攻略");
        post.setContent("火锅和川菜推荐。");

        List<Document> documents = (List<Document>) ReflectionTestUtils.invokeMethod(
                ragService, "buildPostDocuments", post, null, List.of("成都", "美食"));

        assertEquals("火锅和川菜推荐。", documents.getFirst().getText());
        assertFalse(documents.getFirst().getText().contains("成都美食攻略"));
        assertEquals(List.of("成都", "美食"), documents.getFirst().getMetadata().get("tags"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldStoreEmptyTagArrayWhenPostHasNoTags() {
        Post post = new Post();
        post.setId(2L);
        post.setTitle("无标签笔记");
        post.setContent("正文。");

        List<Document> documents = (List<Document>) ReflectionTestUtils.invokeMethod(
                ragService, "buildPostDocuments", post, null, null);

        assertEquals(List.of(), documents.getFirst().getMetadata().get("tags"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldParseTagArrayReturnedByElasticsearch() {
        List<String> tags = (List<String>) ReflectionTestUtils.invokeMethod(
                ragService, "parseTags", List.of("成都", " ", "美食"));

        assertEquals(List.of("成都", "美食"), tags);
    }

    @Test
    void shouldRenderTitleTagsAndRawChunkForAgentContext() {
        Document document = new Document("火锅和川菜推荐。", Map.of(
                "title", "成都美食攻略",
                "author", "测试作者",
                "tags", List.of("成都", "美食")));

        String context = ReflectionTestUtils.invokeMethod(ragService, "renderContextText", List.of(document));

        assertEquals("标题: 成都美食攻略\n标签: 成都、美食\n片段: 火锅和川菜推荐。\n\n", context);
        assertFalse(context.contains("测试作者"));
    }

}
