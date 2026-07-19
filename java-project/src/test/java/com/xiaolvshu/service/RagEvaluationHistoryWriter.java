package com.xiaolvshu.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 将显式真实评测结果追加到正式 Markdown 历史文档。
 * <p>
 * 该类只位于测试源码中，普通应用运行不会获得文档写权限。只有评测脚本显式传入记录路径时才会调用，
 * 从而避免日常 {@code mvn test} 或生产启动意外修改 Git 工作区。
 */
final class RagEvaluationHistoryWriter {

    private RagEvaluationHistoryWriter() {
    }

    /**
     * 追加一次已经计算出完整指标的评测记录。
     *
     * @param path 正式评测历史文件
     * @param record 包含代码状态、参数、指标和结论的完整记录
     */
    static void appendSuccess(Path path, SuccessRecord record) throws IOException {
        String markdown = "\n\n---\n\n"
                + "## " + escape(record.timestamp()) + " — " + escape(record.change()) + "\n\n"
                + "### 代码与数据\n\n"
                + "| 项目 | 值 |\n| --- | --- |\n"
                + row("Git 修订", code(record.gitRevision()))
                + row("工作区", record.worktreeState())
                + row("RAG 索引", code(record.indexName()))
                + row("评测集", record.totalCases() + " 条（" + record.positiveCases()
                + " 条正向、" + record.noAnswerCases() + " 条无答案）")
                + row("评测集 SHA-256", code(record.datasetHash()))
                + "\n### 本轮调整\n\n"
                + "- " + escape(record.change()) + "\n"
                + "\n### 参数\n\n"
                + "| 参数 | 值 |\n| --- | ---: |\n"
                + row("Embedding 模型", code(record.embeddingModel()))
                + row("向量维度", record.dimensions())
                + row("Embedding 批量大小", record.embeddingBatchSize())
                + row("topK", record.topK())
                + row("kNN 最低 cosine", decimal(record.vectorMinimumSimilarity()))
                + row("BM25 最低分", decimal(record.bm25MinimumScore()))
                + row("BM25 强单路分", decimal(record.bm25StrongScore()))
                + row("kNN 强单路 cosine", decimal(record.vectorStrongSimilarity()))
                + row("单路融合候选数", record.candidateCount())
                + row("kNN numCandidates", record.numCandidates())
                + row("RRF rankConstant", record.rrfRankConstant())
                + row("每帖最多 chunk", record.maxChunksPerPost())
                + "\n### 指标结果\n\n"
                + "Embedding 批量准备耗时：**" + record.embeddingPreparationMs() + " ms**。\n\n"
                + "| 策略 | Recall@5 | MRR@5 | nDCG@5 | 无答案拒绝率 | 检索 P95 |\n"
                + "| --- | ---: | ---: | ---: | ---: | ---: |\n"
                + metricsRow("旧混合分数相加基线", record.legacy())
                + metricsRow("当前 RRF（拒绝前）", record.beforeRejection())
                + metricsRow("当前 RRF（拒绝后）", record.afterRejection())
                + "\n### 验收状态与结论\n\n"
                + "- 验收状态：**" + (record.accepted() ? "通过" : "未通过") + "**。\n"
                + "- " + escape(record.conclusion()) + "\n";
        appendLocked(path, markdown);
    }

    /**
     * 评测在指标计算前失败时也保留记录，便于区分算法回归与环境故障。
     */
    static void appendFailure(Path path, FailureRecord record) throws IOException {
        String markdown = "\n\n---\n\n"
                + "## " + escape(record.timestamp()) + " — " + escape(record.change()) + "\n\n"
                + "### 执行失败\n\n"
                + "| 项目 | 值 |\n| --- | --- |\n"
                + row("Git 修订", code(record.gitRevision()))
                + row("工作区", record.worktreeState())
                + row("失败阶段", record.stage())
                + row("异常类型", record.exceptionType())
                + row("脱敏摘要", sanitizeError(record.errorSummary()))
                + "\n- 本次未产生完整指标，不得用于判断检索质量变化。\n";
        appendLocked(path, markdown);
    }

    /** 使用文件锁完成单次原子追加，避免并发评测的 Markdown 片段交叉。 */
    private static void appendLocked(Path path, String markdown) throws IOException {
        Path absolutePath = path.toAbsolutePath().normalize();
        Path parent = absolutePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (FileChannel channel = FileChannel.open(absolutePath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
             FileLock ignored = channel.lock()) {
            channel.write(ByteBuffer.wrap(markdown.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private static String metricsRow(String name, Metrics metrics) {
        return "| " + escape(name)
                + " | " + decimal(metrics.recallAt5())
                + " | " + decimal(metrics.mrrAt5())
                + " | " + decimal(metrics.ndcgAt5())
                + " | " + percent(metrics.noAnswerRejectionRate())
                + " | " + metrics.p95LatencyMs() + " ms |\n";
    }

    private static String row(String name, Object value) {
        return "| " + escape(name) + " | " + escape(String.valueOf(value)) + " |\n";
    }

    private static String code(String value) {
        return "`" + escape(value) + "`";
    }

    private static String decimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    private static String percent(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", value * 100.0d);
    }

    /** 转义 Markdown 表格分隔符和换行，防止调整说明破坏文档结构。 */
    private static String escape(String value) {
        return value == null ? "" : value.replace("|", "\\|").replaceAll("[\\r\\n]+", " ").trim();
    }

    /** 失败记录只保留单行、最多 300 字符，不写入堆栈和请求体。 */
    private static String sanitizeError(String value) {
        // 此处只做截断与单行化；Markdown 转义由 row 统一执行，避免管道符被重复转义。
        String sanitized = value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
        return sanitized.length() <= 300 ? sanitized : sanitized.substring(0, 300) + "...";
    }

    /** 一组检索策略的聚合指标。 */
    record Metrics(double recallAt5, double mrrAt5, double ndcgAt5,
                   double noAnswerRejectionRate, long p95LatencyMs) {
    }

    /** 一次指标计算完整的真实评测记录。 */
    record SuccessRecord(
            String timestamp,
            String gitRevision,
            String worktreeState,
            String change,
            String embeddingModel,
            String indexName,
            int dimensions,
            int embeddingBatchSize,
            int topK,
            double vectorMinimumSimilarity,
            double bm25MinimumScore,
            double bm25StrongScore,
            double vectorStrongSimilarity,
            int candidateCount,
            int numCandidates,
            int rrfRankConstant,
            int maxChunksPerPost,
            int totalCases,
            int positiveCases,
            int noAnswerCases,
            String datasetHash,
            long embeddingPreparationMs,
            Metrics legacy,
            Metrics beforeRejection,
            Metrics afterRejection,
            boolean accepted,
            String conclusion) {
    }

    /** 一次在指标完成前中断的真实评测记录。 */
    record FailureRecord(
            String timestamp,
            String gitRevision,
            String worktreeState,
            String change,
            String stage,
            String exceptionType,
            String errorSummary) {
    }
}
