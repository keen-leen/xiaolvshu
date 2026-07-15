package com.xiaolvshu.controller.admin;

import com.xiaolvshu.dto.admin.AdminResult;
import com.xiaolvshu.service.SearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 管理后台 - Elasticsearch 全文索引维护。 */
@RestController
@RequestMapping("/admin/search")
@RequiredArgsConstructor
@Slf4j
public class AdminSearchController {

    private final SearchIndexService searchIndexService;

    /**
     * 同步全部未同步或内容版本落后的已发布笔记。
     * 该操作只写全文索引，不会触发 embedding 或修改 RAG 状态。
     */
    @PostMapping("/sync")
    public AdminResult<Map<String, Integer>> sync() {
        int syncedCount = searchIndexService.syncPendingPosts();
        log.info("管理员触发全文索引同步完成，文档数: {}", syncedCount);
        return AdminResult.success("全文索引同步完成", Map.of("syncedCount", syncedCount));
    }
}
