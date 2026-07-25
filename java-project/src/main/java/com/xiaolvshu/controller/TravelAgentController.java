package com.xiaolvshu.controller;

import com.xiaolvshu.common.Result;
import com.xiaolvshu.dto.TravelConversationMessagesResponse;
import com.xiaolvshu.dto.TravelChatRequest;
import com.xiaolvshu.service.AgentAccessGuard;
import com.xiaolvshu.service.TravelAgentConversationService;
import com.xiaolvshu.service.TravelAgentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai/travel")
@RequiredArgsConstructor
@Slf4j
public class TravelAgentController {

    private final TravelAgentService travelAgentService;
    private final AgentAccessGuard agentAccessGuard;
    private final TravelAgentConversationService conversationService;

    /** 接收用户问题，并通过 SSE 推送安全进度、最终答案和社区引用。 */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<Object>>> chat(
            @Valid @RequestBody TravelChatRequest request,
            HttpServletRequest servletRequest) {
        AgentAccessGuard.Lease lease = agentAccessGuard.acquire(servletRequest);
        log.info("旅行助手流式对话请求 - messageLength: {}", request.getMessage() == null ? 0 : request.getMessage().length());
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    // 设置浏览器不缓存 SSE，避免刷新页面后收到旧事件
                    .cacheControl(CacheControl.noCache())
                    // nginx 反向代理默认会缓冲 SSE，导致浏览器无法及时收到事件。通过 X-Accel-Buffering: no 禁用 nginx 缓冲。
                    .header("X-Accel-Buffering", "no")
                    .body(travelAgentService.chat(request, lease));
        } catch (RuntimeException e) {
            // Flux 尚未交给 Spring MVC 时发生同步失败，只能由 Controller 归还许可。
            lease.close();
            throw e;
        }
    }

    /** 返回 Spring AI ChatMemory 窗口中仍保留的最近对话，用于刷新页面后恢复 UI。 */
    @GetMapping("/conversations/{conversationId}/messages")
    public Result<TravelConversationMessagesResponse> messages(
            @PathVariable String conversationId) {
        return Result.success(conversationService.messages(conversationId));
    }

    /**
     * 清空服务端短期记忆。“重新聊聊”必须同时删除 ChatMemory，
     * 否则页面虽然清空，模型下一轮仍会继续读取旧上下文。
     */
    @DeleteMapping("/conversations/{conversationId}")
    public Result<Void> clear(@PathVariable String conversationId) {
        conversationService.clear(conversationId);
        return Result.success();
    }
}
