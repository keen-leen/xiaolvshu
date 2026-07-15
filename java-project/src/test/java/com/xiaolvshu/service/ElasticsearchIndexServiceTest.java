package com.xiaolvshu.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ElasticsearchIndexServiceTest {

    @Test
    void shouldBuildIndependentVersionedIndexNames() {
        SearchIndexService searchIndex = new SearchIndexService(null, null, null, null, null);
        ElasticsearchRagIndexService ragIndex = new ElasticsearchRagIndexService(null, null);
        ReflectionTestUtils.setField(searchIndex, "indexPrefix", "test_xiaolvshu");
        ReflectionTestUtils.setField(ragIndex, "indexPrefix", "test_xiaolvshu");

        assertEquals("test_xiaolvshu_posts_v1", searchIndex.postIndex());
        assertEquals("test_xiaolvshu_post_chunks_v1", ragIndex.chunkIndex());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRejectEmbeddingWithUnexpectedDimensions() {
        ElasticsearchRagIndexService ragIndex = new ElasticsearchRagIndexService(null, null);
        ReflectionTestUtils.setField(ragIndex, "dimensions", 3);

        List<Float> vector = (List<Float>) ReflectionTestUtils.invokeMethod(
                ragIndex, "toFloatList", (Object) new float[]{0.1f, 0.2f, 0.3f});
        assertEquals(List.of(0.1f, 0.2f, 0.3f), vector);

        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(
                ragIndex, "toFloatList", (Object) new float[]{0.1f, 0.2f}));
    }
}
