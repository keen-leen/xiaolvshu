package com.xiaolvshu.controller;

import com.xiaolvshu.common.Result;
import com.xiaolvshu.dto.TravelChatRequest;
import com.xiaolvshu.dto.TravelChatResponse;
import com.xiaolvshu.service.TravelAiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/ai/travel")
@RequiredArgsConstructor
@Slf4j
public class TravelAiController {

    private final TravelAiService travelAiService;

    @PostMapping("/chat")
    public Result<TravelChatResponse> chat(@Valid @RequestBody TravelChatRequest request) {
        log.info("旅行AI对话请求 - messageLength: {}", request.getMessage() == null ? 0 : request.getMessage().length());
        return Result.success(travelAiService.chat(request));
    }

    /**
     * 流式对话接口，使用SSE实现实时响应。适用于需要逐步展示AI回复的场景，如长文本生成或多轮对话。
     * 请求参数同普通对话接口，响应内容通过SSE分块发送，前端可以实时接收并展示AI回复。
     * 注意：前端需要使用EventSource或类似机制处理SSE流，确保能够正确解析和展示分块数据。
     * produces = MediaType.TEXT_EVENT_STREAM_VALUE指定响应类型为text/event-stream，告诉客户端这是一个SSE流。
     * SseEmitter是Spring提供的用于处理SSE的工具类，支持异步发送数据到客户端。服务端可以通过调用SseEmitter的send方法将数据发送到客户端，最后调用complete方法结束流。
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody TravelChatRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");
        log.info("旅行AI流式请求 - messageLength: {}", request.getMessage() == null ? 0 : request.getMessage().length());
        return travelAiService.chatStream(request);
    }

    /**
     * 向量库数据库同步接口，将笔记数据同步到RAG模块使用的向量数据库中。适用于初始数据导入或定期更新场景。
     * @return
     */
    @PostMapping("/sync")
    public Result<String> syncVectorStore() {
        int count = travelAiService.syncPostNotesToVectorStore();
        return Result.success("RAG向量库同步完成，索引文档数: " + count);
    }
}
