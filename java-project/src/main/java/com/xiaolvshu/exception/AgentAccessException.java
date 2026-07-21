package com.xiaolvshu.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Agent 请求在建立 SSE 连接前的访问控制异常。
 * <p>
 * 不复用普通 {@link BusinessException}，因为项目的通用业务异常会返回 HTTP 200。
 * SSE 客户端必须在解析事件流之前看到真实的 429/503，否则 JSON 错误体会被误当成事件流。
 */
@Getter
public class AgentAccessException extends RuntimeException {

    private final HttpStatus status;

    public AgentAccessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
