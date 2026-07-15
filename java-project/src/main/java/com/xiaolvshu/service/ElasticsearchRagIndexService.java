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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Elasticsearch RAG chunk 索引网关，不包含全文搜索职责。 */
@Service
@RequiredArgsConstructor
public class ElasticsearchRagIndexService {

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

    public void ensureIndex() {
        try {
            if (client.indices().exists(ExistsRequest.of(e -> e.index(chunkIndex()))).value()) {
                return;
            }
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

    public void clearIndex() {
        ensureIndex();
        try {
            client.deleteByQuery(d -> d.index(chunkIndex())
                    .query(q -> q.matchAll(v -> v)).refresh(true));
        } catch (IOException e) {
            throw new IllegalStateException("清空 Elasticsearch RAG chunk 索引失败", e);
        }
    }

    public void replaceChunks(Long postId, List<Document> documents) {
        ensureIndex();
        deleteChunks(postId);
        for (Document document : documents) {
            Map<String, Object> metadata = document.getMetadata();
            Map<String, Object> source = new HashMap<>(metadata);
            source.put("postId", postId);
            source.put("text", document.getText());
            source.put("embedding", toFloatList(embeddingModel.embed(document.getText())));
            int chunkNumber = Integer.parseInt(String.valueOf(metadata.getOrDefault("chunkIndex", 0)));
            try {
                client.index(i -> i.index(chunkIndex()).id(postId + "-" + chunkNumber).document(source));
            } catch (IOException e) {
                throw new IllegalStateException("写入 Elasticsearch RAG chunk 失败: " + postId, e);
            }
        }
    }

    public void deleteChunks(Long postId) {
        ensureIndex();
        try {
            DeleteByQueryResponse ignored = client.deleteByQuery(d -> d.index(chunkIndex())
                    .query(q -> q.term(t -> t.field("postId").value(postId))));
        } catch (ElasticsearchException e) {
            if (e.status() != 404) {
                throw e;
            }
        } catch (IOException e) {
            throw new IllegalStateException("删除 Elasticsearch RAG chunk 失败: " + postId, e);
        }
    }

    public List<Document> hybridSearch(String text, int topK) {
        ensureIndex();
        List<Float> vector = toFloatList(embeddingModel.embed(text));
        try {
            Query hybridQuery = Query.of(q -> q.bool(b -> b.minimumShouldMatch("1")
                    .should(sh -> sh.multiMatch(mm -> mm.query(text).fields("title^3", "tags^2", "text")))
                    .should(sh -> sh.knn(k -> k.field("embedding").queryVector(vector)
                            .numCandidates((long) Math.max(numCandidates, topK * 10))))));
            SearchResponse<Map> response = client.search(s -> s.index(chunkIndex()).size(topK)
                    .query(hybridQuery), Map.class);
            List<Document> documents = new ArrayList<>();
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
            throw new IllegalStateException("Elasticsearch RAG 检索失败", e);
        }
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
