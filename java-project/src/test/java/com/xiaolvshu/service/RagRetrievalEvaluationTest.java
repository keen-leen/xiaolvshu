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
import java.util.ArrayList;
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

    @Autowired
    private RagIndexService ragIndexService;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Test
    void optimizedRetrievalShouldBeatLegacyHybridQuery() throws Exception {
        List<EvaluationCase> cases = loadEvaluationCases();

        /*
         * 50 条查询先按配置批量生成向量，不再让新旧策略各调用一次远程 API。
         * 这不仅将默认请求数从约 100 次降为 5 次，还保证两种策略使用完全相同的查询向量，
         * 避免模型端的微小波动污染排序对比。
         */
        long embeddingStarted = System.nanoTime();
        List<List<Float>> queryVectors = ragIndexService.embedTexts(
                cases.stream().map(EvaluationCase::query).toList());
        long embeddingPreparationMs = (System.nanoTime() - embeddingStarted) / 1_000_000L;

        // 在同一份索引、同一批查询和同一批预生成向量上连续执行新旧策略。
        EvaluationMetrics baseline = evaluate(cases, queryVectors, this::legacySearch);
        EvaluationMetrics optimized = evaluate(cases, queryVectors,
                (query, vector) -> postIds(ragIndexService.hybridSearch(query, vector, TOP_K)));

        System.out.printf("RAG embedding preparation: %dms, queries=%d, batchSize=%d%n",
                embeddingPreparationMs, cases.size(), ragIndexService.embeddingBatchSize());
        System.out.printf("RAG baseline: %s%n", baseline);
        System.out.printf("RAG optimized: %s%n", optimized);

        assertTrue(optimized.ndcgAt5() >= baseline.ndcgAt5() * 1.15d,
                () -> "优化后 nDCG@5 未达到基线的 115%: baseline=" + baseline + ", optimized=" + optimized);
        assertTrue(optimized.noAnswerRejectionRate() >= baseline.noAnswerRejectionRate(),
                () -> "优化后无答案拒绝率不得低于基线: baseline=" + baseline + ", optimized=" + optimized);
        assertTrue(optimized.p95LatencyMs() <= 2_000L,
                () -> "优化后检索 P95 超过 2 秒: " + optimized.p95LatencyMs() + "ms");
    }

    private List<EvaluationCase> loadEvaluationCases() throws IOException {
        // 评测数据作为只读测试资源提交，保证每次调参使用完全相同的问题和相关帖子标注。
        try (InputStream input = getClass().getResourceAsStream("/rag-evaluation.json")) {
            if (input == null) {
                throw new IllegalStateException("找不到 RAG 评测集 rag-evaluation.json");
            }
            return new ObjectMapper().readValue(input, new TypeReference<>() {
            });
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
