package com.xiaolvshu.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class TravelChatRequest {

    @NotBlank(message = "消息内容不能为空")
    private String message;

    private Integer topK = 5;

    private List<ChatMessage> history;

    @Data
    public static class ChatMessage {
        private String role;
        private String content;
    }
}
