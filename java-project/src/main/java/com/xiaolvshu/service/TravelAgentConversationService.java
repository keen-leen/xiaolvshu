package com.xiaolvshu.service;

import com.xiaolvshu.context.UserContext;
import com.xiaolvshu.dto.TravelConversationMessagesResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Agent 会话 ID、身份隔离和最近消息查询。
 *
 * <p>浏览器只看到随机 UUID，真正写入 ChatMemory 的 key 会再加上 userId 或 anonymous 前缀。
 * 因此两个登录用户即使提交相同 UUID 也不会共享上下文；匿名 UUID 则相当于高熵会话凭证。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TravelAgentConversationService {

    private final ChatMemory chatMemory;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.agent.memory.retention-days:30}")
    private int retentionDays;

    /**
     * 首次请求对话时生成会话ID，后续请求原样带回。
     * 将 conversationId 转为 storageKey，避免用户伪造 conversationId 访问其他用户的上下文。
     * @param requestedConversationId
     * @return
     */
    public Conversation resolve(String requestedConversationId) {
        UUID publicId;
        if (requestedConversationId == null || requestedConversationId.isBlank()) {
            publicId = UUID.randomUUID();
        } else {
            try {
                // 把字符串形式的 UUID 转为 UUID 对象，避免后续拼接 storageKey 时出现大小写不一致。
                publicId = UUID.fromString(requestedConversationId.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("conversationId格式不正确");
            }
        }
        return new Conversation(publicId.toString(), storageKey(publicId));
    }

    public TravelConversationMessagesResponse messages(String conversationId) {
        Conversation conversation = resolveRequired(conversationId);
        List<TravelConversationMessagesResponse.MessageItem> messages = chatMemory.get(conversation.storageKey())
                .stream()
                .filter(message -> message.getMessageType() == MessageType.USER
                        || message.getMessageType() == MessageType.ASSISTANT)
                .map(this::toResponse)
                .toList();
        return new TravelConversationMessagesResponse(conversation.publicId(), messages);
    }

    public void clear(String conversationId) {
        chatMemory.clear(resolveRequired(conversationId).storageKey());
    }

    /**
     * JDBC ChatMemory 没有 TTL。每天删除“整段会话最后一条消息也已过期”的 conversation，
     * 而不是逐条删除旧消息，避免从一个完整 user/assistant 轮次中间截断历史。
     */
    @Scheduled(cron = "${app.agent.memory.cleanup-cron:0 30 3 * * *}")
    public void cleanupInactiveConversations() {
        Instant cutoff = Instant.now().minus(Math.max(1, retentionDays), ChronoUnit.DAYS);
        int deleted = jdbcTemplate.update("""
                DELETE FROM SPRING_AI_CHAT_MEMORY
                WHERE conversation_id IN (
                    SELECT conversation_id FROM (
                        SELECT conversation_id
                        FROM SPRING_AI_CHAT_MEMORY
                        GROUP BY conversation_id
                        HAVING MAX(`timestamp`) < ?
                    ) expired
                )
                """, Timestamp.from(cutoff));
        if (deleted > 0) {
            log.info("已清理过期旅行Agent记忆消息: {} 条", deleted);
        }
    }

    private Conversation resolveRequired(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId不能为空");
        }
        return resolve(conversationId);
    }

    /**
     * 生成 ChatMemory 存储 key，避免用户伪造 conversationId 访问其他用户的上下文。
     * userId + publicId 作为唯一标识，anonymous + publicId 作为匿名会话标识。
     */
    private String storageKey(UUID publicId) {
        Long userId = UserContext.getUserId();
        return userId == null
                ? "travel-agent:anonymous:" + publicId
                : "travel-agent:user:" + userId + ":" + publicId;
    }

    private TravelConversationMessagesResponse.MessageItem toResponse(Message message) {
        Instant createdAt = message.getMetadata().values().stream()
                .filter(Instant.class::isInstance)
                .map(Instant.class::cast)
                .findFirst()
                .orElse(null);
        return new TravelConversationMessagesResponse.MessageItem(
                message.getMessageType() == MessageType.USER ? "user" : "assistant",
                message.getText(),
                createdAt);
    }

    public record Conversation(String publicId, String storageKey) {
    }
}
