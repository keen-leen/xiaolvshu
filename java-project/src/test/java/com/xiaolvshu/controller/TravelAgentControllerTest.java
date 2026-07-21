package com.xiaolvshu.controller;

import com.xiaolvshu.exception.AgentAccessException;
import com.xiaolvshu.exception.TravelAgentExceptionHandler;
import com.xiaolvshu.service.AgentAccessGuard;
import com.xiaolvshu.service.TravelAgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TravelAgentControllerTest {

    private AgentAccessGuard accessGuard;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        accessGuard = mock(AgentAccessGuard.class);
        TravelAgentController controller = new TravelAgentController(
                mock(TravelAgentService.class), accessGuard);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new TravelAgentExceptionHandler())
                .build();
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
