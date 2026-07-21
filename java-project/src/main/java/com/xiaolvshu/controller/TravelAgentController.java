package com.xiaolvshu.controller;

import com.xiaolvshu.dto.TravelChatRequest;
import com.xiaolvshu.service.AgentAccessGuard;
import com.xiaolvshu.service.TravelAgentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/ai/travel")
@RequiredArgsConstructor
@Slf4j
public class TravelAgentController {

    private final TravelAgentService travelAgentService;
    private final AgentAccessGuard agentAccessGuard;

    /**
     * 旅行助手对话入口，接收用户消息并通过 SSE 持续推送 Agent 执行过程中的步骤、工具调用结果、最终答案和相关引用信息。
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody TravelChatRequest request,
                           HttpServletRequest servletRequest,
                           HttpServletResponse response) {
        AgentAccessGuard.Lease lease = agentAccessGuard.acquire(servletRequest);
        prepareSseResponse(response);
        log.info("旅行助手流式对话请求 - messageLength: {}", request.getMessage() == null ? 0 : request.getMessage().length());
        try {
            return travelAgentService.chat(request, lease);
        } catch (RuntimeException e) {
            // 如果在注册 SSE 结束回调前失败，控制器负责归还并发许可。
            lease.close();
            throw e;
        }
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
