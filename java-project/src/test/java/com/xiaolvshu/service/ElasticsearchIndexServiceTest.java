package com.xiaolvshu.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ElasticsearchIndexServiceTest {

    @Test
    void shouldBuildIndependentVersionedIndexNames() {
        SearchIndexService searchIndex = new SearchIndexService(null, null, null, null, null);
        RagIndexService ragIndex = new RagIndexService(null, null);
        ReflectionTestUtils.setField(searchIndex, "indexPrefix", "test_xiaolvshu");
        ReflectionTestUtils.setField(ragIndex, "indexPrefix", "test_xiaolvshu");

        assertEquals("test_xiaolvshu_posts_v1", searchIndex.postIndex());
        assertEquals("test_xiaolvshu_post_chunks_v1", ragIndex.chunkIndex());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRejectEmbeddingWithUnexpectedDimensions() {
        RagIndexService ragIndex = new RagIndexService(null, null);
        ReflectionTestUtils.setField(ragIndex, "dimensions", 3);

        List<Float> vector = (List<Float>) ReflectionTestUtils.invokeMethod(
                ragIndex, "toFloatList", (Object) new float[]{0.1f, 0.2f, 0.3f});
        assertEquals(List.of(0.1f, 0.2f, 0.3f), vector);

        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(
                ragIndex, "toFloatList", (Object) new float[]{0.1f, 0.2f}));
    }

    @Test
    void shouldBuildEmbeddingTextWithoutAuthorMetadata() {
        RagIndexService ragIndex = new RagIndexService(null, null);
        Document document = new Document("火锅和川菜推荐。", Map.of(
                "title", "成都美食攻略",
                "author", "测试作者",
                "tags", List.of("成都", "美食")));

        String embeddingText = ReflectionTestUtils.invokeMethod(ragIndex, "buildEmbeddingText", document);

        assertEquals("标题：成都美食攻略\n标签：成都、美食\n正文：火锅和川菜推荐。", embeddingText);
    }

    @Test
    void shouldOmitBlankOptionalMetadataFromEmbeddingText() {
        RagIndexService ragIndex = new RagIndexService(null, null);
        Document document = new Document("纯正文", Map.of(
                "title", " ",
                "tags", List.of()));

        String embeddingText = ReflectionTestUtils.invokeMethod(ragIndex, "buildEmbeddingText", document);

        assertEquals("正文：纯正文", embeddingText);
    }
}
