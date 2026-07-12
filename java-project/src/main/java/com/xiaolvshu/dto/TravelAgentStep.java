package com.xiaolvshu.dto;

import lombok.Data;

@Data
public class TravelAgentStep {

    private Integer step;

    private String action;

    private String thought;

    private TravelToolCall toolCall;

    private TravelToolResult toolResult;
}
