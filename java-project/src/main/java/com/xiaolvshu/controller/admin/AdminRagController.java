package com.xiaolvshu.controller.admin;

import com.xiaolvshu.dto.admin.AdminResult;
import com.xiaolvshu.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 管理后台 - RAG 向量索引维护。 */
@RestController
@RequestMapping("/admin/rag")
@RequiredArgsConstructor
@Slf4j
public class AdminRagController {

    private final RagService ragService;

    /** 增量生成未向量化或内容已更新笔记的 RAG chunks。 */
    @PostMapping("/sync")
    public AdminResult<Map<String, Integer>> sync() {
        int indexedChunkCount = ragService.syncPostNotesToVectorStore();
        log.info("管理员触发 RAG 增量同步完成，chunk 数: {}", indexedChunkCount);
        return AdminResult.success("RAG 向量增量同步完成",
                Map.of("indexedChunkCount", indexedChunkCount));
    }
}
