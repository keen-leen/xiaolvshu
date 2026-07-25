package com.xiaolvshu.controller;

import com.xiaolvshu.dto.TravelAgentSsePayload;
import com.xiaolvshu.exception.AgentAccessException;
import com.xiaolvshu.exception.TravelAgentExceptionHandler;
import com.xiaolvshu.service.AgentAccessGuard;
import com.xiaolvshu.service.CacheService;
import com.xiaolvshu.service.TravelAgentConversationService;
import com.xiaolvshu.service.TravelAgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TravelAgentControllerTest {

    private AgentAccessGuard accessGuard;
    private TravelAgentService travelAgentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        accessGuard = mock(AgentAccessGuard.class);
        travelAgentService = mock(TravelAgentService.class);
        TravelAgentController controller = new TravelAgentController(
                travelAgentService, accessGuard,
                mock(TravelAgentConversationService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new TravelAgentExceptionHandler())
                .build();
    }

    @Test
    void shouldLetSpringMvcWriteFluxAsSse() throws Exception {
        CacheService cacheService = mock(CacheService.class);
        when(cacheService.isRateLimitAllowed(any(), any(), anyLong(), anyLong()))
                .thenReturn(true);
        AgentAccessGuard.Lease lease = new AgentAccessGuard(
                cacheService, 60, 5, 20, 1, false)
                .acquire(new MockHttpServletRequest());
        when(accessGuard.acquire(any())).thenReturn(lease);
        when(travelAgentService.chat(any(), any())).thenReturn(Flux.just(
                ServerSentEvent.<Object>builder()
                        .event("meta")
                        .data(new TravelAgentSsePayload.Meta(
                                "run-1", 4, "conversation-1"))
                        .build(),
                ServerSentEvent.<Object>builder()
                        .event("done")
                        .data(new TravelAgentSsePayload.Done(
                                "run-1", "conversation-1", "completed", 12))
                        .build()));

        MvcResult mvcResult = mockMvc.perform(post("/ai/travel/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"message\":\"成都三日游\",\"topK\":5}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        /*
         * Flux 是 Spring MVC 的异步返回值：第一次 dispatch 建立订阅，asyncDispatch 后
         * 才能断言框架最终写出的 SSE 文本。业务代码不需要 SseEmitter 或手工 subscribe。
         */
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(content().string(containsString("event:meta")))
                .andExpect(content().string(containsString("\"protocol_version\":4")))
                .andExpect(content().string(containsString("event:done")));
    }

    @Test
    void shouldReturnRealBadRequestBeforeOpeningSse() throws Exception {
        mockMvc.perform(post("/ai/travel/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\",\"topK\":11}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void shouldReturnRealTooManyRequestsBeforeOpeningSse() throws Exception {
        when(accessGuard.acquire(any())).thenThrow(new AgentAccessException(
                HttpStatus.TOO_MANY_REQUESTS, "Agent请求过于频繁，请稍后重试"));

        mockMvc.perform(post("/ai/travel/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"成都三日游\",\"topK\":5}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.message").value("Agent请求过于频繁，请稍后重试"));
    }
}
