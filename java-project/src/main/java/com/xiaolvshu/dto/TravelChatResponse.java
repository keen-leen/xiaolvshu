package com.xiaolvshu.dto;

import lombok.Data;

import java.util.List;

@Data
public class TravelChatResponse {

    private String answer;

    private Boolean ragEnabled;

    private String model;

    private List<TravelNoteReference> references;

    private List<TravelAgentStep> agentSteps;

    private List<TravelToolCall> toolCalls;

    @Data
    public static class TravelNoteReference {
        private Long postId;
        private String title;
        private String author;
        private String summary;
        private String link;
        private List<String> tags;
        private Double score;
    }
}
