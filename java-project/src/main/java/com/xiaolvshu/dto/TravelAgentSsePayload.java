package com.xiaolvshu.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 旅行 Agent SSE 事件的数据部分。
 *
 * <p>事件名称由 {@code ServerSentEvent.event} 表达，这些 record 只描述 data。
 * 使用强类型对象后，Spring MVC 会通过项目统一的 Jackson SNAKE_CASE 配置自动生成
 * {@code run_id}、{@code conversation_id} 等字段，不再手写 Map 和 JSON 字符串。</p>
 */
public final class TravelAgentSsePayload {

    private TravelAgentSsePayload() {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Meta(
            String runId,
            int protocolVersion,
            String conversationId) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Status(
            String code,
            String message) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Done(
            String runId,
            String conversationId,
            String finishReason,
            long elapsedMs) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Error(
            String code,
            String message,
            boolean retryable,
            String runId,
            String conversationId) {
    }
}
