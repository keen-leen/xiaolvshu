package com.xiaolvshu.service;

import com.xiaolvshu.dto.TravelConversationMessagesResponse;
import com.xiaolvshu.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TravelAgentConversationServiceTest {

    private final ChatMemory chatMemory = mock(ChatMemory.class);
    private final TravelAgentConversationService service =
            new TravelAgentConversationService(chatMemory, mock(JdbcTemplate.class));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldNamespaceSamePublicIdByCurrentUser() {
        String publicId = "20b1c884-8e44-4fe9-b7bf-e2b29b1598da";

        authenticate(7L);
        String firstStorageKey = service.resolve(publicId).storageKey();
        authenticate(8L);
        String secondStorageKey = service.resolve(publicId).storageKey();

        assertEquals("travel-agent:user:7:" + publicId, firstStorageKey);
        assertEquals("travel-agent:user:8:" + publicId, secondStorageKey);
        assertNotEquals(firstStorageKey, secondStorageKey);
    }

    @Test
    void shouldReuseGeneratedPublicIdInFollowingRequest() {
        TravelAgentConversationService.Conversation first = service.resolve(null);
        TravelAgentConversationService.Conversation second = service.resolve(first.publicId());

        assertEquals(first.publicId(), second.publicId());
        assertEquals(first.storageKey(), second.storageKey());
    }

    @Test
    void shouldRestoreOnlyDisplayableMessages() {
        String publicId = "20b1c884-8e44-4fe9-b7bf-e2b29b1598da";
        String storageKey = "travel-agent:anonymous:" + publicId;
        when(chatMemory.get(storageKey)).thenReturn(List.of(
                new UserMessage("杭州三日游"),
                new AssistantMessage("第一天游西湖")));

        TravelConversationMessagesResponse response = service.messages(publicId);

        assertEquals(publicId, response.conversationId());
        assertEquals(List.of("user", "assistant"),
                response.messages().stream()
                        .map(TravelConversationMessagesResponse.MessageItem::role)
                        .toList());
    }

    @Test
    void shouldRejectMalformedIdAndClearResolvedConversation() {
        assertThrows(IllegalArgumentException.class, () -> service.messages("not-a-uuid"));

        String publicId = "20b1c884-8e44-4fe9-b7bf-e2b29b1598da";
        service.clear(publicId);

        verify(chatMemory).clear("travel-agent:anonymous:" + publicId);
    }

    private void authenticate(long id) {
        User user = new User();
        user.setId(id);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
}
