package com.xiaolvshu.service;

import com.xiaolvshu.dto.TravelAgentSsePayload;
import com.xiaolvshu.dto.TravelChatRequest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 小旅书旅行 Agent 的响应式事件编排。
 *
 * <p>
 * 本类直接返回 {@code Flux<ServerSentEvent<Object>>}。Spring MVC 7 会订阅该 Flux，
 * 将元素写成 SSE，并在浏览器断开、Servlet 超时或写出失败时取消上游。因此应用不再创建
 * SseEmitter、不再手工 subscribe，也不再保存 Disposable 取消句柄。
 * </p>
 */
@Service
@Slf4j
public class TravelAgentService {

        private final ChatClient travelAgentChatClient;
        private final TravelAgentConversationService conversationService;
        private final MeterRegistry meterRegistry;
        private final long heartbeatSeconds;
        private final long runTimeoutSeconds;
        private final int maxToolCalls;

        public TravelAgentService(
                        ChatClient travelAgentChatClient,
                        TravelAgentConversationService conversationService,
                        MeterRegistry meterRegistry,
                        @Value("${app.agent.heartbeat-seconds:15}") long heartbeatSeconds,
                        @Value("${app.agent.run-timeout-seconds:120}") long runTimeoutSeconds,
                        @Value("${app.agent.max-tool-calls:3}") int maxToolCalls) {
                this.travelAgentChatClient = travelAgentChatClient;
                this.conversationService = conversationService;
                this.meterRegistry = meterRegistry;
                this.heartbeatSeconds = Math.max(1L, heartbeatSeconds);
                this.runTimeoutSeconds = Math.max(1L, runTimeoutSeconds);
                this.maxToolCalls = Math.max(1, maxToolCalls);
        }

        /**
         * 构建一次 Agent 请求的完整 SSE Flux。
         *
         * <p>
         * 该方法只构建流，不主动订阅。Controller 返回 Flux 后由 Spring MVC 建立订阅，
         * 客户端取消会沿着 merge/concat 传播到 ChatClient、工具循环和心跳流。
         * </p>
         */
        public Flux<ServerSentEvent<Object>> chat(
                        TravelChatRequest request,
                        AgentAccessGuard.Lease lease) {
                // 首次请求生成会话ID，后续请求携带会话ID继续对话。分离会话ID和存储Key，避免客户端直接访问存储。
                TravelAgentConversationService.Conversation conversation = conversationService
                                .resolve(request.getConversationId());
                String runId = UUID.randomUUID().toString();
                long startedNanos = System.nanoTime();

                /*
                 * @Tool 方法不是 Flux 的直接元素生产者，需要一个请求级 Sink 把 searching 等
                 * 工具状态接回主事件流。单次请求最多三次检索，unicast 缓冲只承载少量内部状态。
                 */
                Sinks.Many<ServerSentEvent<Object>> toolStatuses = Sinks.many().unicast().onBackpressureBuffer();
                Sinks.One<Void> agentFinished = Sinks.one();

                TravelAgentRunContext runContext = new TravelAgentRunContext(
                                request.getTopK() == null ? 5 : request.getTopK(),
                                maxToolCalls,
                                status -> toolStatuses.tryEmitNext(
                                        event("status", new TravelAgentSsePayload.Status(status.code(), status.message()))));

                // agent流
                Flux<ServerSentEvent<Object>> agentEvents = buildAgentEvents(request, conversation, runContext, runId, startedNanos)
                                // 正常完成、取消和错误都只触发一次 doFinally。
                                .doFinally(signalType -> {
                                        // 记录本次 Agent 运行的耗时和结果。
                                        recordDuration(startedNanos, switch (signalType) {
                                                case ON_COMPLETE -> "success";
                                                case CANCEL -> "cancelled";
                                                default -> "error";
                                        });
                                        // 结束工具状态流
                                        toolStatuses.tryEmitComplete();
                                        // 结束 agentEvents 流，由 heartbeat.takeUntilOther(agentFinished) 监听，在agent流结束后结束心跳流。
                                        agentFinished.tryEmitEmpty();
                                })
                                // 处理模型或工具执行异常，返回 error 事件给客户端。Flux 不会因为异常而中断 SSE 流。
                                .onErrorResume(error -> Flux.just(errorEvent(runId, conversation.publicId(), error)));
                // 心跳流
                Flux<ServerSentEvent<Object>> heartbeat = Flux.interval(Duration.ofSeconds(heartbeatSeconds), Duration.ofSeconds(heartbeatSeconds))
                                // 监听独立的 agent 流结束事件流
                                .takeUntilOther(agentFinished.asMono())
                                .map(ignored -> ServerSentEvent.<Object>builder()
                                                .comment("heartbeat")
                                                .build());
                // 初始事件流，包含 meta 和 thinking 状态。必须在 agentEvents 之前发送，否则客户端可能先收到 chunk。
                Flux<ServerSentEvent<Object>> initialEvents = Flux.just(
                                event("meta", new TravelAgentSsePayload.Meta(runId, 4, conversation.publicId())),
                                event("status", new TravelAgentSsePayload.Status("thinking", "正在分析你的旅行需求")));

                // toolStatuses 必须先订阅，工具执行时产生的状态才不会丢失。
                Flux<ServerSentEvent<Object>> liveEvents = Flux.merge(
                                toolStatuses.asFlux(),
                                agentEvents,
                                heartbeat);

                return Flux.concat(initialEvents, liveEvents).doFinally(ignored -> lease.close());
        }

        /**
         * 构建一次 Agent 请求的 SSE 事件流。
         */
        private Flux<ServerSentEvent<Object>> buildAgentEvents(
                        TravelChatRequest request,
                        TravelAgentConversationService.Conversation conversation,
                        TravelAgentRunContext runContext,
                        String runId,
                        long startedNanos) {
                Mono<Void> deadline = Mono.delay(Duration.ofSeconds(runTimeoutSeconds)).then(Mono.error(new TimeoutException("旅行Agent超过总运行时限")));

                // 主要的模型流式输出，包含最终答案和社区引用。工具调用在内部被 ToolCallingAdvisor 拦截。
                Flux<ServerSentEvent<Object>> chunks = travelAgentChatClient.prompt()
                                .user(request.getMessage())
                                // 该advisor在外层加载最近对话，保证模型在每轮都能看到完整上下文。
                                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversation.storageKey()))
                                // 把请求级 RunContext 放入 ToolContext，供 @Tool 方法访问。用于可信数据传递
                                .toolContext(Map.of(TravelAgentRunContext.TOOL_CONTEXT_KEY, runContext))
                                .stream()
                                .content()
                                // timeout(Duration) 是“相邻元素空闲超时”，持续输出时可能永不触发。
                                // takeUntilOther 监听独立 deadline，确保模型、工具和输出合计最多运行配置时长。
                                .takeUntilOther(deadline)
                                // 空chunk不发送
                                .filter(chunk -> chunk != null && !chunk.isEmpty())
                                // 为chunk编号
                                .index()
                                .concatMap(indexedChunk -> {
                                        // 构建 SSE chunk 事件。
                                        ServerSentEvent<Object> chunkEvent = event("chunk", indexedChunk.getT2());
                                        // 第一个 chunk 前发送的 status 事件
                                        if (indexedChunk.getT1() == 0L) {
                                                return Flux.just(event("status", new TravelAgentSsePayload.Status(
                                                                "writing", "正在生成旅行建议")), chunkEvent);
                                        }
                                        return Flux.just(chunkEvent);
                                });

                /*
                 * 引用由工具在流式执行期间写入 RunContext，必须在模型正常完成后用 defer 读取。
                 * error 路径不会执行 concatWith，因此不会错误发送 refs/done。
                 */
                return chunks.concatWith(Flux.defer(() -> Flux.just(
                                event("refs", runContext.references()),
                                event("done", new TravelAgentSsePayload.Done(runId, conversation.publicId(), "completed", elapsedMs(startedNanos))))));
        }

        // 构建 error 事件，包含 runId、conversationId 和可重试标记。
        // agent 流式执行异常时，Flux 不会中断 SSE 流，而是返回 error 事件给客户端。
        private ServerSentEvent<Object> errorEvent(
                        String runId,
                        String conversationId,
                        Throwable error) {
                Throwable cause = Exceptions.unwrap(error);
                boolean timeout = cause instanceof TimeoutException;
                String code = timeout ? "RUN_TIMEOUT" : "AGENT_FAILED";
                String message = timeout
                                ? "Agent运行超时，请缩小问题范围后重试"
                                : "这次未能完成攻略生成，请稍后重试";
                log.warn("旅行Agent流式调用失败, runId={}, conversationId={}: {}",
                                runId, conversationId, cause.getMessage(), cause);
                return event("error", new TravelAgentSsePayload.Error(
                                code,
                                message,
                                true,
                                runId,
                                conversationId));
        }

        private void recordDuration(long startedNanos, String outcome) {
                Timer.builder("xiaolvshu.agent.run.duration")
                                .description("Travel Agent end-to-end duration")
                                .tag("outcome", outcome)
                                .register(meterRegistry)
                                .record(elapsedMs(startedNanos), TimeUnit.MILLISECONDS);
        }

        private long elapsedMs(long startedNanos) {
                return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        }

        private ServerSentEvent<Object> event(String name, Object data) {
                return ServerSentEvent.<Object>builder()
                                .event(name)
                                .data(data)
                                .build();
        }
}
