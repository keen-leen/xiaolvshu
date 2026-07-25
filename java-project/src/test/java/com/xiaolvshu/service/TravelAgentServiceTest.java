package com.xiaolvshu.service;

import com.xiaolvshu.dto.TravelAgentSsePayload;
import com.xiaolvshu.dto.TravelChatRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.mock.web.MockHttpServletRequest;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TravelAgentServiceTest {

    @Test
    void shouldReturnTypedSseEventsInProtocolOrder() {
        ChatModel model = streamingModel(Flux.just(
                response("第一段"),
                response("第二段")));
        TestFixture fixture = fixture(model, 5);

        List<ServerSentEvent<Object>> events = fixture.service()
                .chat(request(), fixture.lease())
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(events).isNotNull();
        assertThat(events).extracting(ServerSentEvent::event)
                .containsExactly(
                        "meta", "status", "status",
                        "chunk", "chunk", "refs", "done");
        assertThat(events.getFirst().id()).isNull();
        assertThat(events.getFirst().data())
                .isInstanceOfSatisfying(TravelAgentSsePayload.Meta.class,
                        meta -> assertThat(meta.protocolVersion()).isEqualTo(4));
        assertThat(events.get(2).data())
                .isEqualTo(new TravelAgentSsePayload.Status(
                        "writing", "正在生成旅行建议"));
        assertPermitWasReleased(fixture);
    }

    @Test
    void shouldConvertModelFailureToTerminalErrorEvent() {
        ChatModel model = streamingModel(Flux.error(
                new IllegalStateException("provider unavailable")));
        TestFixture fixture = fixture(model, 5);

        List<ServerSentEvent<Object>> events = fixture.service()
                .chat(request(), fixture.lease())
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(events).isNotNull();
        assertThat(events).extracting(ServerSentEvent::event)
                .containsExactly("meta", "status", "error");
        assertThat(events.getLast().data())
                .isEqualTo(new TravelAgentSsePayload.Error(
                        "AGENT_FAILED",
                        "这次未能完成攻略生成，请稍后重试",
                        true,
                        ((TravelAgentSsePayload.Meta) events.getFirst().data()).runId(),
                        "conversation-1"));
        assertPermitWasReleased(fixture);
    }

    @Test
    void shouldReleaseLeaseWhenClientCancelsFlux() {
        ChatModel model = streamingModel(Flux.never());
        TestFixture fixture = fixture(model, 5);

        List<ServerSentEvent<Object>> events = fixture.service()
                .chat(request(), fixture.lease())
                .take(2)
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(events).isNotNull();
        assertThat(events).extracting(ServerSentEvent::event)
                .containsExactly("meta", "status");
        assertPermitWasReleased(fixture);
    }

    @Test
    void shouldApplyTotalDeadlineEvenWhenModelKeepsSendingChunks() {
        /*
         * 模型每 100ms 都有新片段，因此“相邻元素空闲超时”永远不会触发。
         * 该用例证明当前限制是整次运行的绝对截止时间。
         */
        ChatModel model = streamingModel(Flux.interval(Duration.ofMillis(100))
                .map(index -> response("片段" + index)));
        TestFixture fixture = fixture(model, 1);

        List<ServerSentEvent<Object>> events = fixture.service()
                .chat(request(), fixture.lease())
                .collectList()
                .block(Duration.ofSeconds(3));

        assertThat(events).isNotNull();
        assertThat(events).extracting(ServerSentEvent::event)
                .contains("chunk")
                .doesNotContain("done")
                .endsWith("error");
        assertThat(events.getLast().data())
                .isInstanceOfSatisfying(TravelAgentSsePayload.Error.class,
                        error -> assertThat(error.code()).isEqualTo("RUN_TIMEOUT"));
        assertPermitWasReleased(fixture);
    }

    private TestFixture fixture(ChatModel model, long timeoutSeconds) {
        TravelAgentConversationService conversationService =
                mock(TravelAgentConversationService.class);
        when(conversationService.resolve(any())).thenReturn(
                new TravelAgentConversationService.Conversation(
                        "conversation-1", "storage-1"));
        CacheService cacheService = mock(CacheService.class);
        when(cacheService.isRateLimitAllowed(any(), any(), anyLong(), anyLong()))
                .thenReturn(true);
        AgentAccessGuard accessGuard = new AgentAccessGuard(
                cacheService, 60, 5, 20, 1, false);
        AgentAccessGuard.Lease lease = accessGuard.acquire(requestContext());
        TravelAgentService service = new TravelAgentService(
                ChatClient.create(model),
                conversationService,
                new SimpleMeterRegistry(),
                60,
                timeoutSeconds,
                3);
        return new TestFixture(service, lease, accessGuard);
    }

    private ChatModel streamingModel(Flux<ChatResponse> responses) {
        ChatModel model = mock(ChatModel.class);
        // ChatClient 会复制模型默认选项；测试模型也必须提供一份最小可变配置。
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(model.stream(any(Prompt.class))).thenReturn(responses);
        return model;
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(
                new Generation(new AssistantMessage(text))));
    }

    private TravelChatRequest request() {
        TravelChatRequest request = new TravelChatRequest();
        request.setMessage("成都三日游");
        request.setTopK(5);
        return request;
    }

    private MockHttpServletRequest requestContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    /**
     * 并发上限为 1。流结束后如果还能再次获取许可，就证明 doFinally 已在正常结束、
     * 错误或取消路径释放上一份 Lease，不需要暴露内部 Semaphore 供测试读取。
     */
    private void assertPermitWasReleased(TestFixture fixture) {
        AgentAccessGuard.Lease nextLease =
                fixture.accessGuard().acquire(requestContext());
        nextLease.close();
    }

    private record TestFixture(
            TravelAgentService service,
            AgentAccessGuard.Lease lease,
            AgentAccessGuard accessGuard) {
    }
}
