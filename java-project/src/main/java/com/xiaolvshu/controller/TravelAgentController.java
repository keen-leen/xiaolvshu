package com.xiaolvshu.controller;

import com.xiaolvshu.common.Result;
import com.xiaolvshu.dto.TravelChatRequest;
import com.xiaolvshu.service.TravelAgentService;
import com.xiaolvshu.service.RagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/ai/travel")
@RequiredArgsConstructor
@Slf4j
public class TravelAgentController {

    private final TravelAgentService travelAgentService;
    private final RagService ragService;

    /**
     * 旅行助手对话入口，接收用户消息并通过 SSE 持续推送 Agent 执行过程中的步骤、工具调用结果、最终答案和相关引用信息。
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody TravelChatRequest request, HttpServletResponse response) {
        prepareSseResponse(response);
        log.info("旅行助手流式对话请求 - messageLength: {}", request.getMessage() == null ? 0 : request.getMessage().length());
        return travelAgentService.chat(request);
    }

    /**
     * 向量库数据库同步接口，将笔记数据同步到RAG模块使用的向量数据库中。适用于初始数据导入或定期更新场景。
     */
    @PostMapping("/sync")
    public Result<String> syncVectorStore(
            @RequestParam(defaultValue = "incremental") String mode) {
        boolean full = "full".equalsIgnoreCase(mode);
        int count = ragService.syncPostChunksToElasticsearch(full);
        return Result.success("Elasticsearch " + (full ? "全量" : "增量")
                + "同步完成，RAG索引文档数: " + count);
    }

    /**
     * 设置 SSE 必要响应头，关闭代理缓冲并保持连接。
     */
    private void prepareSseResponse(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");
    }
}
