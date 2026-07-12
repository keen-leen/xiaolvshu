package com.xiaolvshu.dto;

import lombok.Data;

import java.util.Map;

@Data
public class TravelToolCall {

    private Integer step;

    private String toolName;

    private Map<String, Object> arguments;

    private String reason;

    private String status;
}
