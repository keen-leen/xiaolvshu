package com.xiaolvshu.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    @SuppressWarnings("unchecked")
    void shouldEmbedTwentyThreeTextsAsTenTenAndThreeWithoutChangingOrder() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        RagIndexService ragIndex = new RagIndexService(null, embeddingModel);
        ReflectionTestUtils.setField(ragIndex, "dimensions", 3);
        ReflectionTestUtils.setField(ragIndex, "embeddingBatchSize", 10);

        /*
         * 用文本尾部序号构造可识别向量，使断言同时验证三个性质：请求被拆成 10+10+3、
         * 返回数量与输入一致、跨批拼接后仍保持原始顺序。
         */
        when(embeddingModel.embed(anyList())).thenAnswer(invocation -> {
            List<String> batch = invocation.getArgument(0);
            List<float[]> vectors = new ArrayList<>();
            for (String text : batch) {
                float sequence = Float.parseFloat(text.substring("text-".length()));
                vectors.add(new float[]{sequence, sequence + 0.1f, sequence + 0.2f});
            }
            return vectors;
        });

        List<String> texts = java.util.stream.IntStream.range(0, 23)
                .mapToObj(index -> "text-" + index)
                .toList();
        List<List<Float>> vectors = ragIndex.embedTexts(texts);

        org.mockito.ArgumentCaptor<List<String>> batches = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(embeddingModel, times(3)).embed(batches.capture());
        assertEquals(List.of(10, 10, 3), batches.getAllValues().stream().map(List::size).toList());
        assertEquals(23, vectors.size());
        assertEquals(0.0f, vectors.getFirst().getFirst());
        assertEquals(10.0f, vectors.get(10).getFirst());
        assertEquals(22.0f, vectors.getLast().getFirst());
    }

    @Test
    void shouldRejectBatchWhenEmbeddingCountDoesNotMatchInputCount() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        RagIndexService ragIndex = new RagIndexService(null, embeddingModel);
        ReflectionTestUtils.setField(ragIndex, "dimensions", 3);
        ReflectionTestUtils.setField(ragIndex, "embeddingBatchSize", 10);

        // 模拟兼容接口异常漏掉一条返回值；服务必须立即失败，不能让后续 chunk 与错误向量错位。
        when(embeddingModel.embed(anyList())).thenReturn(List.of(new float[]{1.0f, 2.0f, 3.0f}));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> ragIndex.embedTexts(List.of("first", "second")));
        assertTrue(exception.getMessage().contains("返回数量不匹配"));
    }

    @Test
    void shouldRejectBatchWhenAnyEmbeddingHasUnexpectedDimensions() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        RagIndexService ragIndex = new RagIndexService(null, embeddingModel);
        ReflectionTestUtils.setField(ragIndex, "dimensions", 3);
        ReflectionTestUtils.setField(ragIndex, "embeddingBatchSize", 10);

        // 即使返回数量正确，只要其中一条维度不符，也不能把不兼容向量写入既有 mapping。
        when(embeddingModel.embed(anyList())).thenReturn(List.of(
                new float[]{1.0f, 2.0f, 3.0f},
                new float[]{1.0f, 2.0f}));

        assertThrows(IllegalStateException.class,
                () -> ragIndex.embedTexts(List.of("first", "second")));
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

    @Test
    void shouldBoostCandidateReturnedByBothRetrievalRoutes() {
        // 文档 2 同时位于 BM25 第二名和 kNN 第一名，两路 RRF 贡献相加后应超过两个单路第一名候选。
        List<Document> documents = RagIndexService.reciprocalRankFusion(
                List.of(candidate("1-0", 1L, 0), candidate("2-0", 2L, 0)),
                List.of(candidate("2-0", 2L, 0), candidate("3-0", 3L, 0)),
                3, 60, 2);

        assertEquals(List.of("2", "1", "3"), postIds(documents));
    }

    @Test
    void shouldKeepCandidatesReturnedByOnlyOneRetrievalRoute() {
        // RRF 不是求交集：专有名词可能只被 BM25 命中，语义改写也可能只被 kNN 命中，两类结果都必须保留。
        List<Document> documents = RagIndexService.reciprocalRankFusion(
                List.of(candidate("1-0", 1L, 0)),
                List.of(candidate("2-0", 2L, 0)),
                5, 60, 2);

        assertEquals(List.of("1", "2"), postIds(documents));
    }

    @Test
    void shouldLimitChunksFromSamePostBeforeApplyingTopK() {
        // 帖子 1 的前三个 chunk 排名都很高，但多样性限制为 2 后，第三个位置必须让给帖子 2。
        List<Document> documents = RagIndexService.reciprocalRankFusion(
                List.of(
                        candidate("1-0", 1L, 0),
                        candidate("1-1", 1L, 1),
                        candidate("1-2", 1L, 2),
                        candidate("2-0", 2L, 0)),
                List.of(),
                3, 60, 2);

        assertEquals(List.of("1", "1", "2"), postIds(documents));
    }

    @Test
    void shouldUseStableDocumentIdTieBreaker() {
        // 两个候选都只在各自列表中排名第一，RRF 分数和最好名次完全相同，此时按文档 ID 保证稳定排序。
        List<Document> documents = RagIndexService.reciprocalRankFusion(
                List.of(candidate("2-0", 2L, 0)),
                List.of(candidate("1-0", 1L, 0)),
                2, 60, 2);

        assertEquals(List.of("1", "2"), postIds(documents));
    }

    @Test
    void shouldReturnEmptyResultForEmptyCandidatesOrNonPositiveTopK() {
        // 无候选不能制造占位文档；topK 非正数时也应直接返回空列表，避免下游出现越界或错误上下文。
        assertTrue(RagIndexService.reciprocalRankFusion(List.of(), List.of(), 5, 60, 2).isEmpty());
        assertTrue(RagIndexService.reciprocalRankFusion(
                List.of(candidate("1-0", 1L, 0)), List.of(), 0, 60, 2).isEmpty());
    }

    private RagIndexService.SearchCandidate candidate(String id, Long postId, int chunkIndex) {
        // 构造最小 ES _source 形状；score 对 RRF 排名无影响，固定为 1 可以突出测试只依赖列表名次。
        return new RagIndexService.SearchCandidate(id, Map.of(
                "postId", String.valueOf(postId),
                "chunkIndex", chunkIndex,
                "text", "chunk-" + id), 1.0d);
    }

    private List<String> postIds(List<Document> documents) {
        // 测试只关心融合后的帖子顺序，不比较与排序无关的展示 metadata。
        return documents.stream()
                .map(document -> String.valueOf(document.getMetadata().get("postId")))
                .toList();
    }
}
