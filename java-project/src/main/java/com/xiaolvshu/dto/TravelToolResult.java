package com.xiaolvshu.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class TravelToolResult {

    private Integer step;

    private String toolName;

    private Map<String, Object> arguments;

    private Boolean success;

    private String content;

    private String error;

    private Long elapsedMs;

    private List<TravelChatResponse.TravelNoteReference> references;
}
