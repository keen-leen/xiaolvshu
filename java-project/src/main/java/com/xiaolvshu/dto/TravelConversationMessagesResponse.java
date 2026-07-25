package com.xiaolvshu.dto;

import java.time.Instant;
import java.util.List;

/**
 * Agent 最近会话消息。
 *
 * <p>ChatMemory 是提供给模型的短期上下文窗口，不是永久聊天档案，因此这里只返回
 * 窗口中仍然存在的 user/assistant 消息，工具中间消息不会作为产品历史暴露。</p>
 */
public record TravelConversationMessagesResponse(
        String conversationId,
        List<MessageItem> messages) {

    public record MessageItem(
            String role,
            String content,
            Instant createdAt) {
    }
}
