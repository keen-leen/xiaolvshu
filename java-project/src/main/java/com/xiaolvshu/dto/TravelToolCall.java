package com.xiaolvshu.dto;

import lombok.Data;

import java.util.Map;

@Data
public class TravelToolCall {

    /** Provider 生成的工具调用 ID，用于串联模型请求、工具结果与观测记录。 */
    private String callId;

    private Integer step;

    private String toolName;

    private Map<String, Object> arguments;

    private String reason;

    private String status;
}
