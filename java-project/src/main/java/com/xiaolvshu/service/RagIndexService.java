package com.xiaolvshu.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Elasticsearch RAG chunk 索引网关，不包含全文搜索职责。 */
@Service
@RequiredArgsConstructor
public class RagIndexService {

    private final ElasticsearchClient client;
    private final EmbeddingModel embeddingModel;

    @Value("${app.rag.index-prefix:xiaolvshu}")
    private String indexPrefix;

    @Value("${app.rag.dimensions:1024}")
    private int dimensions;

    @Value("${app.rag.num-candidates:100}")
    private int numCandidates;

    public String chunkIndex() {
        return indexPrefix + "_post_chunks_v1";
    }

    /**
     * 确保 Elasticsearch RAG chunk 索引存在，若不存在则创建。
     * xiaolvshu_post_chunks_v1 索引结构：
     * - postId: long
     * - chunkIndex: int
     * - text: text (zh analyzer)
     * - title: text (zh analyzer)
     * - author: keyword
     * - summary: text (index=false)
     * - link: keyword (index=false)
     * - tags: keyword
     * - embedding: dense_vector (dims=1024, index=true, similarity=cosine)
     */
    public void ensureIndex() {
        try {
            if (client.indices().exists(ExistsRequest.of(e -> e.index(chunkIndex()))).value()) {
                return;
            }
            // 创建索引结构
            client.indices().create(c -> c.index(chunkIndex()).settings(s -> s
                            .analysis(a -> a.analyzer("zh", an -> an.custom(ca -> ca.tokenizer("smartcn_tokenizer")))))
                    .mappings(m -> m
                            .properties("postId", p -> p.long_(v -> v))
                            .properties("chunkIndex", p -> p.integer(v -> v))
                            .properties("text", p -> p.text(v -> v.analyzer("zh")))
                            .properties("title", p -> p.text(v -> v.analyzer("zh")))
                            .properties("author", p -> p.keyword(v -> v))
                            .properties("summary", p -> p.text(v -> v.index(false)))
                            .properties("link", p -> p.keyword(v -> v.index(false)))
                            .properties("tags", p -> p.keyword(v -> v))
                            .properties("embedding", p -> p.denseVector(v -> v
                                    .dims(dimensions).index(true).similarity("cosine")))));
        } catch (IOException e) {
            throw new IllegalStateException("初始化 Elasticsearch RAG chunk 索引失败", e);
        }
    }

    /**
     * 清空 Elasticsearch RAG posts chunks 索引。
     */
    public void clearIndex() {
        ensureIndex();
        try {
            client.deleteByQuery(d -> d.index(chunkIndex())
                    .query(q -> q.matchAll(v -> v)).refresh(true));
        } catch (IOException e) {
            throw new IllegalStateException("清空 Elasticsearch RAG posts chunks 索引失败", e);
        }
    }

    /**
     * 替换指定笔记的所有 chunk 文档，若不存在则创建。
     *
     * @param postId    笔记 ID
     * @param documents chunk 文档列表
     */
    public void replaceChunks(Long postId, List<Document> documents) {
        // 1. 确保索引存在
        ensureIndex();
        // 2. 删除原有 chunk 文档
        deleteChunks(postId);
        // 3. 写入新的 chunk 文档
        for (Document document : documents) {
            Map<String, Object> metadata = document.getMetadata();
            Map<String, Object> source = new HashMap<>(metadata);
            source.put("postId", postId);
            source.put("text", document.getText());
            source.put("embedding", toFloatList(embeddingModel.embed(buildEmbeddingText(document))));
            int chunkNumber = Integer.parseInt(String.valueOf(metadata.getOrDefault("chunkIndex", 0)));
            try {
                client.index(i -> i.index(chunkIndex()).id(postId + "-" + chunkNumber).document(source));
            } catch (IOException e) {
                throw new IllegalStateException("写入 Elasticsearch RAG posts chunks 失败: " + postId, e);
            }
        }
    }

    /**
     * 删除指定笔记的所有 chunk 文档。
     *
     * @param postId 笔记 ID
     */
    public void deleteChunks(Long postId) {
        ensureIndex();
        try {
            client.deleteByQuery(d -> d.index(chunkIndex())
                    .query(q -> q.term(t -> t.field("postId").value(postId))));
        } catch (ElasticsearchException e) {
            if (e.status() != 404) {
                throw e;
            }
        } catch (IOException e) {
            throw new IllegalStateException("删除 Elasticsearch RAG posts chunks 失败: " + postId, e);
        }
    }

    /**
     * 检索 RAG chunks，返回按相关性排序的文档列表。
     *
     * @param text 查询文本
     * @param topK 返回条数
     * @return 文档列表
     */
    public List<Document> hybridSearch(String text, int topK) {
        // 1. 确保索引存在
        ensureIndex();
        // 2. 生成查询向量
        List<Float> vector = toFloatList(embeddingModel.embed(text));
        try {
            // 3. 构建混合查询：文本匹配 + 向量相似度
            Query hybridQuery = Query.of(q -> q.bool(b -> b.minimumShouldMatch("1")
                    .should(sh -> sh.multiMatch(mm -> mm.query(text).fields("title^3", "tags^2", "text")))
                    .should(sh -> sh.knn(k -> k.field("embedding").queryVector(vector)
                            .numCandidates((long) Math.max(numCandidates, topK * 10))))));
            // 4. 执行查询
            SearchResponse<Map> response = client.search(s -> s.index(chunkIndex()).size(topK)
                    .query(hybridQuery), Map.class);
            List<Document> documents = new ArrayList<>();
            // 5. 解析查询结果
            for (Hit<Map> hit : response.hits().hits()) {
                if (hit.source() == null || hit.score() == null) {
                    continue;
                }
                Map<String, Object> source = new HashMap<>(hit.source());
                String content = String.valueOf(source.remove("text"));
                source.remove("embedding");
                source.put("score", hit.score());
                documents.add(new Document(content, source));
            }
            return documents;
        } catch (IOException e) {
            throw new IllegalStateException("Elasticsearch RAG posts chunks 检索失败", e);
        }
    }

    /**
     * 构建只用于向量化的文本：标题和标签提供笔记级语义，正文保留 chunk 的局部信息。
     * 作者、摘要和链接等展示字段不参与向量化，避免引入无关语义。
     */
    private String buildEmbeddingText(Document document) {
        List<String> parts = new ArrayList<>();
        Object title = document.getMetadata().get("title");
        if (title != null && !title.toString().isBlank()) {
            parts.add("标题：" + title.toString().trim());
        }

        Object tagValue = document.getMetadata().get("tags");
        if (tagValue instanceof Collection<?> values) {
            List<String> tags = values.stream()
                    .filter(value -> value != null && !value.toString().isBlank())
                    .map(value -> value.toString().trim())
                    .toList();
            if (!tags.isEmpty()) {
                parts.add("标签：" + String.join("、", tags));
            }
        }

        String text = document.getText() == null ? "" : document.getText().trim();
        parts.add("正文：" + text);
        return String.join("\n", parts);
    }

    private List<Float> toFloatList(float[] values) {
        if (values == null || values.length != dimensions) {
            throw new IllegalStateException("Embedding 维度不匹配，期望 " + dimensions + "，实际 "
                    + (values == null ? 0 : values.length));
        }
        List<Float> result = new ArrayList<>(values.length);
        for (float value : values) {
            result.add(value);
        }
        return result;
    }
}
