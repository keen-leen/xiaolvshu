package com.xiaolvshu.service;

import com.xiaolvshu.entity.User;
import com.xiaolvshu.exception.AgentAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentAccessGuardTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldLimitAnonymousByRemoteAddressAndIgnoreUntrustedForwardedHeader() {
        CacheService cacheService = mock(CacheService.class);
        when(cacheService.isRateLimitAllowed(eq("travel-agent"), eq("ip:127.0.0.1"), anyLong(), eq(5L)))
                .thenReturn(true);
        AgentAccessGuard guard = new AgentAccessGuard(cacheService, 60, 5, 20, 1, false);
        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", "203.0.113.10");

        AgentAccessGuard.Lease lease = guard.acquire(request);
        lease.close();

        verify(cacheService).isRateLimitAllowed("travel-agent", "ip:127.0.0.1", 60, 5);
    }

    @Test
    void shouldUseAuthenticatedUserQuota() {
        CacheService cacheService = mock(CacheService.class);
        when(cacheService.isRateLimitAllowed("travel-agent", "user:42", 60, 20)).thenReturn(true);
        User user = new User();
        user.setId(42L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
        AgentAccessGuard guard = new AgentAccessGuard(cacheService, 60, 5, 20, 1, false);

        try (AgentAccessGuard.Lease ignored = guard.acquire(request())) {
            verify(cacheService).isRateLimitAllowed("travel-agent", "user:42", 60, 20);
        }
    }

    @Test
    void shouldRejectWhenRateLimitIsExhausted() {
        CacheService cacheService = mock(CacheService.class);
        when(cacheService.isRateLimitAllowed("travel-agent", "ip:127.0.0.1", 60, 5)).thenReturn(false);
        AgentAccessGuard guard = new AgentAccessGuard(cacheService, 60, 5, 20, 1, false);

        AgentAccessException exception = assertThrows(AgentAccessException.class,
                () -> guard.acquire(request()));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
    }

    @Test
    void shouldFailClosedWhenRedisIsUnavailable() {
        CacheService cacheService = mock(CacheService.class);
        when(cacheService.isRateLimitAllowed("travel-agent", "ip:127.0.0.1", 60, 5))
                .thenThrow(new IllegalStateException("redis unavailable"));
        AgentAccessGuard guard = new AgentAccessGuard(cacheService, 60, 5, 20, 1, false);

        AgentAccessException exception = assertThrows(AgentAccessException.class,
                () -> guard.acquire(request()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
    }

    @Test
    void shouldReleaseConcurrentPermitOnlyOnce() {
        CacheService cacheService = mock(CacheService.class);
        when(cacheService.isRateLimitAllowed(eq("travel-agent"), eq("ip:127.0.0.1"), anyLong(), anyLong()))
                .thenReturn(true);
        AgentAccessGuard guard = new AgentAccessGuard(cacheService, 60, 5, 20, 1, false);
        AgentAccessGuard.Lease first = guard.acquire(request());

        AgentAccessException busy = assertThrows(AgentAccessException.class,
                () -> guard.acquire(request()));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, busy.getStatus());

        first.close();
        first.close();
        assertDoesNotThrow(() -> guard.acquire(request()).close());
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ai/travel/chat");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
