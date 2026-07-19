package com.xiaolvshu.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    /**
     * BM25 和 kNN 每一路进入 RRF 的候选数。
     * <p>
     * 候选数必须大于最终 topK，否则两路召回还没有充分交叉就被截断，
     * RRF 无法利用“同一文档同时被文本和向量命中”这一强信号。
     */
    @Value("${app.rag.candidate-count:30}")
    private int candidateCount;

    /**
     * RRF 公式 {@code 1 / (k + rank)} 中的 k。
     * 较大的值会缩小头部名次差异，让两路都稳定出现的候选优先于单路的偶然高分。
     */
    @Value("${app.rag.rrf-rank-constant:60}")
    private int rrfRankConstant;

    /**
     * 同一篇笔记允许进入模型上下文的最大 chunk 数。
     * 这是检索结果的多样性约束，避免长笔记的相邻分块占满所有 topK 位置。
     */
    @Value("${app.rag.max-chunks-per-post:2}")
    private int maxChunksPerPost;

    /**
     * kNN 候选的原始 cosine 最低相似度。
     * <p>
     * Elasticsearch 接口中的 {@code similarity} 与返回结果的 {@code _score} 不是同一量纲，
     * 因此该值只传入 kNN 查询，不能用来过滤 BM25 分数或 RRF 分数。
     */
    @Value("${app.rag.similarity-threshold:0.45}")
    private float similarityThreshold;

    /**
     * 单次发送给 Embedding API 的最大文本数量。
     * <p>
     * 默认值 10 同时兼容 text-embedding-v4 和 qwen3.7-text-embedding；所有批量入口都经过该值分片，
     * 避免同步大量帖子或运行离线评测时为每个 chunk 单独发起远程请求。
     */
    @Value("${app.rag.embedding-batch-size:10}")
    private int embeddingBatchSize;

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
        replaceChunksBatch(Map.of(postId, documents));
    }

    /**
     * 批量替换多篇笔记的 RAG chunks。
     * <p>
     * 方法先为本批全部 chunk 生成向量，只有所有 Embedding 都成功后才开始删除旧文档和写入新文档。
     * 这样远程模型在中间批次失败时不会提前破坏 Elasticsearch 中仍可检索的旧版本。
     * Elasticsearch 写入若在中途失败，调用方不会更新 MySQL 向量化状态；后续增量同步会再次幂等替换本批帖子。
     *
     * @param documentsByPost 按帖子 ID 分组的 chunk 文档；Map 顺序会作为写入顺序保留
     * @return 本批成功写入的 chunk 总数
     */
    public int replaceChunksBatch(Map<Long, List<Document>> documentsByPost) {
        if (documentsByPost == null || documentsByPost.isEmpty()) {
            return 0;
        }
        ensureIndex();

        List<ChunkInput> chunkInputs = new ArrayList<>();
        List<String> embeddingTexts = new ArrayList<>();
        for (Map.Entry<Long, List<Document>> entry : documentsByPost.entrySet()) {
            Long postId = entry.getKey();
            List<Document> documents = entry.getValue();
            if (postId == null || documents == null || documents.isEmpty()) {
                continue;
            }
            for (Document document : documents) {
                if (document == null) {
                    continue;
                }
                chunkInputs.add(new ChunkInput(postId, document));
                embeddingTexts.add(buildEmbeddingText(document));
            }
        }
        if (chunkInputs.isEmpty()) {
            return 0;
        }

        // 批量向量与输入严格按下标对应；embedTexts 会校验返回数量和每条向量的维度。
        List<List<Float>> vectors = embedTexts(embeddingTexts);
        Map<Long, List<PreparedChunk>> preparedByPost = new LinkedHashMap<>();
        for (int i = 0; i < chunkInputs.size(); i++) {
            ChunkInput input = chunkInputs.get(i);
            preparedByPost.computeIfAbsent(input.postId(), ignored -> new ArrayList<>())
                    .add(new PreparedChunk(input.document(), vectors.get(i)));
        }

        int writtenChunks = 0;
        for (Map.Entry<Long, List<PreparedChunk>> entry : preparedByPost.entrySet()) {
            Long postId = entry.getKey();
            // 一篇帖子的新向量已经全部准备好，此时再删除旧 chunks，缩短不可检索窗口。
            deleteChunks(postId);
            for (PreparedChunk prepared : entry.getValue()) {
                writePreparedChunk(postId, prepared);
                writtenChunks++;
            }
        }
        return writtenChunks;
    }

    /** 将已经完成向量化的单个 chunk 写入 Elasticsearch，禁止在该阶段再次调用远程 Embedding API。 */
    private void writePreparedChunk(Long postId, PreparedChunk prepared) {
        Document document = prepared.document();
        Map<String, Object> metadata = document.getMetadata();
        Map<String, Object> source = new HashMap<>(metadata);
        source.put("postId", postId);
        source.put("text", document.getText());
        source.put("embedding", prepared.vector());
        int chunkNumber = Integer.parseInt(String.valueOf(metadata.getOrDefault("chunkIndex", 0)));
        try {
            client.index(i -> i.index(chunkIndex()).id(postId + "-" + chunkNumber).document(source));
        } catch (IOException e) {
            throw new IllegalStateException("写入 Elasticsearch RAG posts chunks 失败: " + postId, e);
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
        List<Float> vector = embedTexts(List.of(text)).getFirst();
        return hybridSearch(text, vector, topK);
    }

    /**
     * 使用调用方已经生成的查询向量执行混合检索。
     * <p>
     * 该入口主要供离线评测复用同一个查询向量，保证新旧算法对比不会重复请求 Embedding API；
     * 普通业务调用仍使用 {@link #hybridSearch(String, int)}，公开行为保持不变。
     */
    List<Document> hybridSearch(String text, List<Float> vector, int topK) {
        ensureIndex();
        validateVector(vector);
        // 如果调用方要求的 topK 大于默认候选数，至少要保证每路能召回 topK 条。
        int safeCandidateCount = Math.max(topK, candidateCount);
        // BM25 和 kNN 的原始分数量纲不同，不能直接相加；先独立召回，再只使用名次做 RRF。
        List<SearchCandidate> lexicalCandidates = lexicalSearch(text, safeCandidateCount);
        List<SearchCandidate> vectorCandidates = vectorSearch(vector, safeCandidateCount);
        return reciprocalRankFusion(lexicalCandidates, vectorCandidates, topK,
                rrfRankConstant, maxChunksPerPost);
    }

    /**
     * 执行纯 BM25 文本召回。
     * <p>
     * 标题和标签对目的地、景点名、店名等精确实体更重要，因此分别使用 3 倍和 2 倍权重；
     * embedding 字段体积大且不参与文本匹配，从 {@code _source} 中排除可减少网络传输和反序列化开销。
     */
    private List<SearchCandidate> lexicalSearch(String text, int limit) {
        try {
            SearchResponse<Map> response = client.search(s -> s.index(chunkIndex()).size(limit)
                    .source(src -> src.filter(f -> f.excludes("embedding")))
                    .query(q -> q.multiMatch(mm -> mm.query(text)
                            .fields("title^3", "tags^2", "text"))), Map.class);
            return mapCandidates(response);
        } catch (IOException e) {
            throw new IllegalStateException("Elasticsearch RAG BM25 检索失败", e);
        }
    }

    /**
     * 执行纯 kNN 向量召回。
     * <p>
     * {@code k} 决定返回给 RRF 的数量；{@code numCandidates} 决定 Elasticsearch ANN 搜索时的候选池。
     * 候选池始终不小于 {@code limit * 4}，避免增大融合候选数时向量召回率反而下降。
     */
    private List<SearchCandidate> vectorSearch(List<Float> vector, int limit) {
        try {
            SearchResponse<Map> response = client.search(s -> s.index(chunkIndex()).size(limit)
                    .source(src -> src.filter(f -> f.excludes("embedding")))
                    .knn(k -> k.field("embedding")
                            .queryVector(vector)
                            .k((long) limit)
                            .numCandidates((long) Math.max(numCandidates, limit * 4))
                            // similarity 是原始 cosine 门槛，不是 Elasticsearch 返回的 _score。
                            .similarity(similarityThreshold)), Map.class);
            return mapCandidates(response);
        } catch (IOException e) {
            throw new IllegalStateException("Elasticsearch RAG kNN 检索失败", e);
        }
    }

    /**
     * 将 Elasticsearch 命中转换为统一候选对象。
     * 后续融合只依赖文档 ID 和在各自列表中的名次，原始分数仅作为内部诊断信息保留。
     */
    private List<SearchCandidate> mapCandidates(SearchResponse<Map> response) {
        List<SearchCandidate> candidates = new ArrayList<>();
        for (Hit<Map> hit : response.hits().hits()) {
            if (hit.id() == null || hit.source() == null || hit.score() == null) {
                continue;
            }
            candidates.add(new SearchCandidate(hit.id(), new HashMap<>(hit.source()), hit.score()));
        }
        return candidates;
    }

    /**
     * 用 Reciprocal Rank Fusion 融合两路排名，并限制同一篇笔记占用的 chunk 数。
     * <p>
     * 每个候选在一路排名中的贡献为 {@code 1 / (rankConstant + rank)}。
     * 同一文档如果同时被 BM25 和 kNN 命中，两份贡献相加，因而自然优先于仅在单路中出现的弱候选。
     * 融合后再按 postId 限制 chunk 数，最后截取 topK，否则“先截断、后去重”可能导致结果数不足。
     * 该方法不依赖 Elasticsearch，便于用固定候选集验证融合结果。
     *
     * @param lexicalCandidates BM25 按相关性从高到低排列的候选
     * @param vectorCandidates kNN 按相关性从高到低排列的候选
     * @param topK 最终返回的 chunk 上限
     * @param rankConstant RRF 平滑常量
     * @param maxChunksPerPost 同一 postId 可保留的 chunk 上限
     * @return 已完成融合、多样性限制和 topK 截断的文档
     */
    static List<Document> reciprocalRankFusion(
            List<SearchCandidate> lexicalCandidates,
            List<SearchCandidate> vectorCandidates,
            int topK,
            int rankConstant,
            int maxChunksPerPost) {
        if (topK <= 0) {
            return List.of();
        }

        Map<String, FusedCandidate> fusedById = new LinkedHashMap<>();
        // 使用同一张 Map 累加两路贡献，文档 ID 是 chunk 级唯一键，不会错误合并同帖的不同 chunk。
        addRankedCandidates(fusedById, lexicalCandidates, rankConstant);
        addRankedCandidates(fusedById, vectorCandidates, rankConstant);

        // RRF 同分时先看单路最好名次，再用文档 ID 打破平局，保证相同输入每次都得到稳定顺序。
        List<FusedCandidate> ranked = fusedById.values().stream()
                .sorted(Comparator.comparingDouble(FusedCandidate::rrfScore).reversed()
                        .thenComparingInt(FusedCandidate::bestRank)
                        .thenComparing(candidate -> candidate.candidate().id()))
                .toList();

        int safeMaxChunksPerPost = Math.max(1, maxChunksPerPost);
        Map<String, Integer> chunksByPost = new HashMap<>();
        List<Document> documents = new ArrayList<>();
        for (FusedCandidate fused : ranked) {
            SearchCandidate candidate = fused.candidate();
            Object postId = candidate.source().get("postId");
            // 正常索引文档都有 postId；若历史脏数据缺失该字段，回退到文档 ID，避免所有缺失项被归为同一篇笔记。
            String postKey = postId == null ? candidate.id() : String.valueOf(postId);
            int usedChunks = chunksByPost.getOrDefault(postKey, 0);
            if (usedChunks >= safeMaxChunksPerPost) {
                continue;
            }

            Map<String, Object> metadata = new HashMap<>(candidate.source());
            String content = String.valueOf(metadata.remove("text"));
            // embedding 仅用于检索，不能进入 Spring AI Document metadata，否则会浪费内存并增大工具结果。
            metadata.remove("embedding");
            documents.add(new Document(content, metadata));
            chunksByPost.put(postKey, usedChunks + 1);
            if (documents.size() >= topK) {
                break;
            }
        }
        return documents;
    }

    private static void addRankedCandidates(
            Map<String, FusedCandidate> fusedById,
            List<SearchCandidate> candidates,
            int rankConstant) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        int safeRankConstant = Math.max(1, rankConstant);
        for (int i = 0; i < candidates.size(); i++) {
            SearchCandidate candidate = candidates.get(i);
            if (candidate == null || candidate.id() == null || candidate.source() == null) {
                continue;
            }
            int rank = i + 1;
            // rank 从 1 开始，与信息检索领域的排名定义保持一致。
            double contribution = 1.0d / (safeRankConstant + rank);
            fusedById.compute(candidate.id(), (id, existing) -> existing == null
                    ? new FusedCandidate(candidate, contribution, rank)
                    : new FusedCandidate(existing.candidate(), existing.rrfScore() + contribution,
                            Math.min(existing.bestRank(), rank)));
        }
    }

    /**
     * 单路检索返回的内部候选。
     *
     * @param id Elasticsearch chunk 文档 ID，作为两路结果合并的唯一键
     * @param source 不含 embedding 的原始文档字段
     * @param score 该检索路径的原始分数，仅用于诊断，不能跨 BM25 与 kNN 直接比较
     */
    static record SearchCandidate(String id, Map<String, Object> source, double score) {
    }

    /** RRF 聚合过程中的内部状态，同时保存累计分数和两路中的最好名次用于稳定排序。 */
    private record FusedCandidate(SearchCandidate candidate, double rrfScore, int bestRank) {
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

    /**
     * 按配置大小批量生成文本向量，并保持输出与输入一一对应。
     * <p>
     * Spring AI 的批量接口会把一个字符串列表作为一次 Embedding 请求发送；这里主动分片，防止调用方传入
     * 超过模型单批上限的文本数量。任一批次失败时立即终止，异常中只包含批次位置，不泄露待向量化正文。
     *
     * @param texts 待向量化文本，顺序不可改变
     * @return 与 texts 等长、同顺序的 1024 维浮点向量
     */
    List<List<Float>> embedTexts(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        int batchSize = embeddingBatchSize();
        List<List<Float>> result = new ArrayList<>(texts.size());
        int totalBatches = (texts.size() + batchSize - 1) / batchSize;
        for (int start = 0, batchNumber = 1; start < texts.size(); start += batchSize, batchNumber++) {
            int end = Math.min(start + batchSize, texts.size());
            List<String> batch = List.copyOf(texts.subList(start, end));
            List<float[]> batchVectors;
            try {
                batchVectors = embeddingModel.embed(batch);
            } catch (Exception e) {
                throw new IllegalStateException("批量生成 Embedding 失败，批次 " + batchNumber + "/" + totalBatches
                        + "，文本数 " + batch.size(), e);
            }
            if (batchVectors == null || batchVectors.size() != batch.size()) {
                throw new IllegalStateException("Embedding 返回数量不匹配，批次 " + batchNumber + "/" + totalBatches
                        + "，期望 " + batch.size() + "，实际 "
                        + (batchVectors == null ? 0 : batchVectors.size()));
            }
            for (float[] values : batchVectors) {
                result.add(toFloatList(values));
            }
        }
        return result;
    }

    /** 返回经过安全边界修正的批量大小，避免错误配置导致死循环或超出当前兼容模型的单批上限。 */
    int embeddingBatchSize() {
        return Math.max(1, Math.min(10, embeddingBatchSize));
    }

    /** 校验调用方预生成的查询向量，防止错误维度进入 Elasticsearch kNN 查询。 */
    private void validateVector(List<Float> vector) {
        if (vector == null || vector.size() != dimensions) {
            throw new IllegalStateException("Embedding 维度不匹配，期望 " + dimensions + "，实际 "
                    + (vector == null ? 0 : vector.size()));
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

    /** 尚未生成向量的 chunk 输入。 */
    private record ChunkInput(Long postId, Document document) {
    }

    /** 已生成向量、可直接写入 Elasticsearch 的 chunk。 */
    private record PreparedChunk(Document document, List<Float> vector) {
    }
}
