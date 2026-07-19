package com.xiaolvshu.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RAG 真实链路离线评测。
 * <p>
 * 默认跳过，避免日常单元测依赖 Elasticsearch 和远程 Embedding 服务。
 * 完成开发环境的 RAG 索引同步后，通过 Maven 系统属性
 * {@code -DrunRagEvaluation=true} 显式运行。不使用应用环境变量作为开关，
 * 避免开发启动脚本导出环境后让普通 {@code mvn test} 意外发起真实网络请求。
 */
@SpringBootTest(
        classes = RagRetrievalEvaluationTest.EvaluationApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "runRagEvaluation", matches = "true")
class RagRetrievalEvaluationTest {

    private static final int TOP_K = 5;
    private static final double MINIMUM_ACCEPTED_RECALL = 0.8867d;
    private static final double MINIMUM_NO_ANSWER_REJECTION_RATE = 0.80d;
    private static final double MAXIMUM_NDCG_DROP = 0.02d;

    @Autowired
    private RagIndexService ragIndexService;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    // 以下参数同时参与检索和评测记录，保证历史文档反映本次真正生效的配置。
    @Value("${spring.ai.openai.embedding.options.model:unknown}")
    private String embeddingModelName;

    @Value("${app.rag.dimensions:1024}")
    private int dimensions;

    @Value("${app.rag.similarity-threshold:0.45}")
    private double vectorMinimumSimilarity;

    @Value("${app.rag.bm25-min-score:2.0}")
    private double bm25MinimumScore;

    @Value("${app.rag.bm25-strong-score:12.3}")
    private double bm25StrongScore;

    @Value("${app.rag.vector-strong-similarity:0.55}")
    private double vectorStrongSimilarity;

    @Value("${app.rag.candidate-count:30}")
    private int candidateCount;

    @Value("${app.rag.num-candidates:100}")
    private int numCandidates;

    @Value("${app.rag.rrf-rank-constant:60}")
    private int rrfRankConstant;

    @Value("${app.rag.max-chunks-per-post:2}")
    private int maxChunksPerPost;

    @Test
    void optimizedRetrievalShouldBeatLegacyHybridQuery() throws Exception {
        String stage = "加载评测集";
        try {
            LoadedEvaluationCases loaded = loadEvaluationCases();
            List<EvaluationCase> cases = loaded.cases();

            /*
             * 50 条查询先按配置批量生成向量，不再让三种策略分别调用远程 API。
             * 同一查询向量同时供旧基线、拒绝前 RRF 和拒绝后 RRF 使用，避免模型波动污染对比。
             */
            stage = "批量生成查询向量";
            long embeddingStarted = System.nanoTime();
            List<List<Float>> queryVectors = ragIndexService.embedTexts(
                    cases.stream().map(EvaluationCase::query).toList());
            long embeddingPreparationMs = (System.nanoTime() - embeddingStarted) / 1_000_000L;

            stage = "执行旧混合检索基线";
            EvaluationMetrics legacy = evaluate(cases, queryVectors, this::legacySearch);
            stage = "执行拒绝前 RRF";
            EvaluationMetrics beforeRejection = evaluate(cases, queryVectors,
                    (query, vector) -> postIds(
                            ragIndexService.hybridSearchWithoutRejection(query, vector, TOP_K)));
            stage = "执行拒绝后 RRF";
            EvaluationMetrics afterRejection = evaluate(cases, queryVectors,
                    (query, vector) -> postIds(ragIndexService.hybridSearch(query, vector, TOP_K)));

            boolean accepted = meetsAcceptance(beforeRejection, afterRejection);
            System.out.printf("RAG embedding preparation: %dms, queries=%d, batchSize=%d%n",
                    embeddingPreparationMs, cases.size(), ragIndexService.embeddingBatchSize());
            System.out.printf("RAG legacy: %s%n", legacy);
            System.out.printf("RAG before rejection: %s%n", beforeRejection);
            System.out.printf("RAG after rejection: %s%n", afterRejection);
            printNoAnswerFalsePositiveDiagnostics(cases, queryVectors);

            stage = "写入评测历史";
            appendSuccessRecord(loaded, embeddingPreparationMs, legacy,
                    beforeRejection, afterRejection, accepted);

            assertTrue(afterRejection.noAnswerRejectionRate() >= MINIMUM_NO_ANSWER_REJECTION_RATE,
                    () -> "无答案拒绝率低于 80%: " + afterRejection);
            assertTrue(afterRejection.recallAt5() >= MINIMUM_ACCEPTED_RECALL,
                    () -> "拒绝后 Recall@5 低于 0.8867: " + afterRejection);
            assertTrue(afterRejection.ndcgAt5() >= beforeRejection.ndcgAt5() - MAXIMUM_NDCG_DROP,
                    () -> "拒绝后 nDCG@5 相对拒绝前下降超过 0.02: before="
                            + beforeRejection + ", after=" + afterRejection);
            assertTrue(afterRejection.p95LatencyMs() <= 2_000L,
                    () -> "拒绝后检索 P95 超过 2 秒: " + afterRejection.p95LatencyMs() + "ms");
        } catch (Exception e) {
            appendFailureRecord(stage, e);
            throw e;
        }
    }

    private LoadedEvaluationCases loadEvaluationCases() throws Exception {
        // 同时计算资源哈希，使历史指标能够区分“算法变化”与“评测集变化”。
        try (InputStream input = getClass().getResourceAsStream("/rag-evaluation.json")) {
            if (input == null) {
                throw new IllegalStateException("找不到 RAG 评测集 rag-evaluation.json");
            }
            byte[] bytes = input.readAllBytes();
            List<EvaluationCase> cases = new ObjectMapper().readValue(bytes, new TypeReference<>() {
            });
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            return new LoadedEvaluationCases(cases, hash);
        }
    }

    /**
     * 复现优化前 BM25 与 kNN 原始分数直接相加的查询，用作同环境基线。
     * <p>
     * 这里故意保留旧查询的行为，不复用生产代码中的 RRF 和相似度门槛；否则所谓“基线”会随新实现一起变化，
     * 无法判断优化是否真正提升了排序质量。
     */
    private List<Long> legacySearch(String text, List<Float> vector) throws IOException {
        Query hybridQuery = Query.of(q -> q.bool(b -> b.minimumShouldMatch("1")
                .should(sh -> sh.multiMatch(mm -> mm.query(text).fields("title^3", "tags^2", "text")))
                .should(sh -> sh.knn(k -> k.field("embedding").queryVector(vector)
                        .numCandidates(100L)))));
        SearchResponse<Map> response = elasticsearchClient.search(s -> s
                .index(ragIndexService.chunkIndex())
                .size(TOP_K)
                .source(src -> src.filter(f -> f.excludes("embedding")))
                .query(hybridQuery), Map.class);

        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (Hit<Map> hit : response.hits().hits()) {
            if (hit.source() == null) {
                continue;
            }
            Long postId = parseLong(hit.source().get("postId"));
            if (postId != null) {
                result.add(postId);
            }
        }
        return new ArrayList<>(result);
    }

    private EvaluationMetrics evaluate(
            List<EvaluationCase> cases,
            List<List<Float>> queryVectors,
            Retrieval retrieval) throws Exception {
        double recallSum = 0.0d;
        double reciprocalRankSum = 0.0d;
        double ndcgSum = 0.0d;
        int positiveCount = 0;
        int rejectedNoAnswerCount = 0;
        int noAnswerCount = 0;
        List<Long> latencies = new ArrayList<>();

        for (int caseIndex = 0; caseIndex < cases.size(); caseIndex++) {
            EvaluationCase evaluationCase = cases.get(caseIndex);
            List<Float> queryVector = queryVectors.get(caseIndex);
            // P95 只统计 Elasticsearch 检索和应用层排序；Embedding 批量准备耗时已单独输出。
            long started = System.nanoTime();
            List<Long> actual = retrieval.search(evaluationCase.query(), queryVector);
            latencies.add((System.nanoTime() - started) / 1_000_000L);

            if (evaluationCase.expectNoResult()) {
                // 无答案问题单独计算拒绝率，不混入正向查询的排序指标。
                noAnswerCount++;
                if (actual.isEmpty()) {
                    rejectedNoAnswerCount++;
                }
                continue;
            }

            positiveCount++;
            Set<Long> relevant = new HashSet<>(evaluationCase.relevantPostIds());
            // Recall 允许一个问题标注多篇同主题帖子，衡量 top5 覆盖了多少已知相关内容。
            long relevantHits = actual.stream().limit(TOP_K).filter(relevant::contains).count();
            recallSum += relevant.isEmpty() ? 0.0d : (double) relevantHits / relevant.size();
            reciprocalRankSum += reciprocalRank(actual, relevant);
            ndcgSum += ndcg(actual, relevant);
        }

        return new EvaluationMetrics(
                average(recallSum, positiveCount),
                average(reciprocalRankSum, positiveCount),
                average(ndcgSum, positiveCount),
                average(rejectedNoAnswerCount, noAnswerCount),
                percentile95(latencies));
    }

    private double reciprocalRank(List<Long> actual, Set<Long> relevant) {
        // MRR 只关注第一篇相关笔记出现得有多早，第一名命中得 1，第二名命中得 1/2，以此类推。
        for (int i = 0; i < Math.min(TOP_K, actual.size()); i++) {
            if (relevant.contains(actual.get(i))) {
                return 1.0d / (i + 1);
            }
        }
        return 0.0d;
    }

    private double ndcg(List<Long> actual, Set<Long> relevant) {
        // 当前标注使用二元相关性；相关笔记越靠前折损越小，再除以理想排序 DCG 归一化到 0～1。
        double dcg = 0.0d;
        for (int i = 0; i < Math.min(TOP_K, actual.size()); i++) {
            if (relevant.contains(actual.get(i))) {
                dcg += 1.0d / log2(i + 2.0d);
            }
        }
        double idealDcg = 0.0d;
        for (int i = 0; i < Math.min(TOP_K, relevant.size()); i++) {
            idealDcg += 1.0d / log2(i + 2.0d);
        }
        return idealDcg == 0.0d ? 0.0d : dcg / idealDcg;
    }

    private double log2(double value) {
        return Math.log(value) / Math.log(2.0d);
    }

    private double average(double total, int count) {
        return count == 0 ? 0.0d : total / count;
    }

    /** 按本轮已确认的平衡型标准判断拒绝策略是否可接受。 */
    private boolean meetsAcceptance(EvaluationMetrics before, EvaluationMetrics after) {
        return after.noAnswerRejectionRate() >= MINIMUM_NO_ANSWER_REJECTION_RATE
                && after.recallAt5() >= MINIMUM_ACCEPTED_RECALL
                && after.ndcgAt5() >= before.ndcgAt5() - MAXIMUM_NDCG_DROP
                && after.p95LatencyMs() <= 2_000L;
    }

    /**
     * 当脚本显式提供记录路径时，在断言执行前写入完整指标。
     * 因此即使后续 JUnit 因验收不达标而失败，该次调参也不会丢失。
     */
    private void appendSuccessRecord(
            LoadedEvaluationCases loaded,
            long embeddingPreparationMs,
            EvaluationMetrics legacy,
            EvaluationMetrics beforeRejection,
            EvaluationMetrics afterRejection,
            boolean accepted) throws IOException {
        Path recordPath = evaluationRecordPath();
        if (recordPath == null) {
            return;
        }

        int noAnswerCases = (int) loaded.cases().stream().filter(EvaluationCase::expectNoResult).count();
        String conclusion = accepted
                ? "无关结果拒绝、正向召回、排序质量和延迟均达到平衡型验收标准。"
                : "本轮参数未同时满足拒绝率、Recall@5、nDCG@5 和 P95 约束，保留记录供下一轮调参对比。";
        RagEvaluationHistoryWriter.appendSuccess(recordPath,
                new RagEvaluationHistoryWriter.SuccessRecord(
                        currentTimestamp(),
                        evaluationProperty("ragEvaluationGitRevision", "unknown"),
                        evaluationProperty("ragEvaluationWorktreeState", "unknown"),
                        evaluationProperty("ragEvaluationChange", "未填写本轮调整说明"),
                        embeddingModelName,
                        ragIndexService.chunkIndex(),
                        dimensions,
                        ragIndexService.embeddingBatchSize(),
                        TOP_K,
                        vectorMinimumSimilarity,
                        bm25MinimumScore,
                        bm25StrongScore,
                        vectorStrongSimilarity,
                        candidateCount,
                        numCandidates,
                        rrfRankConstant,
                        maxChunksPerPost,
                        loaded.cases().size(),
                        loaded.cases().size() - noAnswerCases,
                        noAnswerCases,
                        loaded.datasetHash(),
                        embeddingPreparationMs,
                        toHistoryMetrics(legacy),
                        toHistoryMetrics(beforeRejection),
                        toHistoryMetrics(afterRejection),
                        accepted,
                        conclusion));
    }

    /** 记录指标计算前的环境或外部服务失败，但不用记录错误覆盖原始异常。 */
    private void appendFailureRecord(String stage, Exception error) {
        Path recordPath = evaluationRecordPath();
        if (recordPath == null) {
            return;
        }
        try {
            RagEvaluationHistoryWriter.appendFailure(recordPath,
                    new RagEvaluationHistoryWriter.FailureRecord(
                            currentTimestamp(),
                            evaluationProperty("ragEvaluationGitRevision", "unknown"),
                            evaluationProperty("ragEvaluationWorktreeState", "unknown"),
                            evaluationProperty("ragEvaluationChange", "未填写本轮调整说明"),
                            stage,
                            error.getClass().getSimpleName(),
                            error.getMessage()));
        } catch (IOException historyError) {
            System.err.println("写入 RAG 评测失败记录时再次失败: " + historyError.getMessage());
        }
    }

    private Path evaluationRecordPath() {
        String value = System.getProperty("ragEvaluationRecordFile");
        return value == null || value.isBlank() ? null : Path.of(value);
    }

    private String evaluationProperty(String name, String defaultValue) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String currentTimestamp() {
        return OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx"));
    }

    private RagEvaluationHistoryWriter.Metrics toHistoryMetrics(EvaluationMetrics metrics) {
        return new RagEvaluationHistoryWriter.Metrics(
                metrics.recallAt5(), metrics.mrrAt5(), metrics.ndcgAt5(),
                metrics.noAnswerRejectionRate(), metrics.p95LatencyMs());
    }

    /**
     * 只对评测集中的无答案样本输出误召回证据，用于判断下一轮应调整哪一条门槛。
     * 日志不包含帖子正文，只输出固定评测问题、文档 ID、命中路数和分数。
     */
    private void printNoAnswerFalsePositiveDiagnostics(
            List<EvaluationCase> cases,
            List<List<Float>> queryVectors) {
        for (int index = 0; index < cases.size(); index++) {
            EvaluationCase evaluationCase = cases.get(index);
            if (!evaluationCase.expectNoResult()) {
                continue;
            }
            List<Document> documents = ragIndexService.hybridSearch(
                    evaluationCase.query(), queryVectors.get(index), TOP_K);
            if (documents.isEmpty()) {
                continue;
            }
            Document top = documents.getFirst();
            System.out.printf(
                    "RAG false positive: query=%s, postId=%s, routes=%s, bm25=%s, cosine=%s%n",
                    evaluationCase.query(),
                    top.getMetadata().get("postId"),
                    top.getMetadata().get("ragRouteCount"),
                    top.getMetadata().get("ragLexicalScore"),
                    top.getMetadata().get("ragVectorSimilarity"));
        }
    }

    private long percentile95(List<Long> values) {
        if (values.isEmpty()) {
            return 0L;
        }
        List<Long> sorted = values.stream().sorted().toList();
        // 使用 nearest-rank 定义：向上取整 95% 位置并转为从 0 开始的列表下标。
        int index = Math.max(0, (int) Math.ceil(sorted.size() * 0.95d) - 1);
        return sorted.get(index);
    }

    private List<Long> postIds(List<Document> documents) {
        // 同一篇帖子可能返回两个 chunk；排序评测以帖子为单位，因此用 LinkedHashSet 保序去重。
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Document document : documents) {
            Long postId = parseLong(document.getMetadata().get("postId"));
            if (postId != null) {
                ids.add(postId);
            }
        }
        return new ArrayList<>(ids);
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * RAG 评测专用的最小 Spring Boot 上下文。
     * <p>
     * 评测只需要 {@link RagIndexService}、Elasticsearch Java Client 和 Spring AI EmbeddingModel。
     * 如果直接加载 {@code XiaolvshuApplication}，会同时创建 JWT、数据库、Redis、RabbitMQ
     * 和 Web 安全等与检索评测无关的 Bean，使测试在进入指标计算前就因缺少无关密钥失败。
     * 因此这里不做业务包扫描，只显式导入 RAG 索引服务，并排除无关自动配置。
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RabbitAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class
    })
    @Import(RagIndexService.class)
    static class EvaluationApplication {
    }

    /** 一条人工标注的查询；无答案样本的 relevantPostIds 为空且 expectNoResult 为 true。 */
    private record EvaluationCase(String query, List<Long> relevantPostIds, boolean expectNoResult) {
    }

    /** 评测条目与原始资源哈希，两者必须作为一个快照一起进入历史记录。 */
    private record LoadedEvaluationCases(List<EvaluationCase> cases, String datasetHash) {
    }

    /** 一次完整策略评测的聚合结果，所有排序指标均以帖子而非 chunk 为统计单位。 */
    private record EvaluationMetrics(
            double recallAt5,
            double mrrAt5,
            double ndcgAt5,
            double noAnswerRejectionRate,
            long p95LatencyMs) {
    }

    @FunctionalInterface
    private interface Retrieval {
        List<Long> search(String query, List<Float> vector) throws Exception;
    }
}
