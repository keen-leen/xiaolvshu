package com.xiaolvshu.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagEvaluationHistoryWriterTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldAppendCompleteSuccessfulEvaluationRecord() throws Exception {
        Path history = temporaryDirectory.resolve("RAG_EVALUATION_HISTORY.md");
        RagEvaluationHistoryWriter.Metrics metrics =
                new RagEvaluationHistoryWriter.Metrics(0.91d, 0.92d, 0.93d, 0.80d, 25L);

        RagEvaluationHistoryWriter.appendSuccess(history,
                new RagEvaluationHistoryWriter.SuccessRecord(
                        "2026-07-19 23:30:00 +08:00",
                        "abc1234",
                        "dirty",
                        "增加无关结果拒绝",
                        "qwen3.7-text-embedding",
                        "xiaolvshu_post_chunks_v1",
                        1024, 10, 5,
                        0.45d, 2.0d, 6.0d, 0.55d,
                        30, 100, 60, 2,
                        50, 40, 10, "dataset-hash", 2500L,
                        metrics, metrics, metrics, true,
                        "所有指标达标。"));

        String content = Files.readString(history);
        assertTrue(content.contains("增加无关结果拒绝"));
        assertTrue(content.contains("qwen3.7-text-embedding"));
        assertTrue(content.contains("80.0%"));
        assertTrue(content.contains("验收状态：**通过**"));
    }

    @Test
    void shouldAppendSanitizedFailureWithoutSecretsOrStackTraceShape() throws Exception {
        Path history = temporaryDirectory.resolve("RAG_EVALUATION_HISTORY.md");

        RagEvaluationHistoryWriter.appendFailure(history,
                new RagEvaluationHistoryWriter.FailureRecord(
                        "2026-07-19 23:31:00 +08:00",
                        "abc1234",
                        "clean",
                        "调整门槛",
                        "生成向量",
                        "IllegalStateException",
                        "第一行\n第二行|附加信息"));

        String content = Files.readString(history);
        assertTrue(content.contains("生成向量"));
        assertTrue(content.contains("第一行 第二行\\|附加信息"));
        // 记录不保存 Java 堆栈形状，避免文档膨胀或泄露内部路径。
        assertFalse(content.contains("\tat "));
    }
}
