package com.xiaolvshu.dto;

import lombok.Data;

@Data
public class TravelToolResult {

    private Integer step;

    private String toolName;

    private Boolean success;

    private String content;

    private String error;

    private Long elapsedMs;
}
