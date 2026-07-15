package com.xiaolvshu.config;

import com.xiaolvshu.utils.JwtTokenUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthenticationFilterTest {

    private JwtTokenUtil jwtTokenUtil;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtTokenUtil = new JwtTokenUtil();
        ReflectionTestUtils.setField(jwtTokenUtil, "secret",
                "xiaolvshu-test-secret-key-must-be-at-least-32-bytes");
        ReflectionTestUtils.setField(jwtTokenUtil, "expiresIn", 60_000L);
        ReflectionTestUtils.setField(jwtTokenUtil, "refreshExpiresIn", 120_000L);
        filter = new JwtAuthenticationFilter(jwtTokenUtil);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldGrantAdminRoleOnlyToAdminToken() throws Exception {
        authenticate(jwtTokenUtil.generateAdminAccessToken(1L, "admin"));
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())));

        SecurityContextHolder.clearContext();
        authenticate(jwtTokenUtil.generateAccessToken(1L, "user"));
        assertFalse(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())));
    }

    @Test
    void shouldNotAuthenticateWithAdminRefreshToken() throws Exception {
        authenticate(jwtTokenUtil.generateAdminRefreshToken(1L, "admin"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private void authenticate(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/search/sync");
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }
}
