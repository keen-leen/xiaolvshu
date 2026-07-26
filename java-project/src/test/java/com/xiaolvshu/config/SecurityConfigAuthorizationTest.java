package com.xiaolvshu.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import com.xiaolvshu.utils.JwtTokenUtil;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端授权矩阵回归测试。
 *
 * <p>这些路径使用轻量探针 Controller，不加载数据库或外部基础设施；测试目标仅是验证
 * SecurityFilterChain 的路径和角色匹配，防止再次用 /auth/** 意外放开管理员接口。</p>
 */
@SpringJUnitWebConfig(SecurityConfigAuthorizationTest.TestConfig.class)
class SecurityConfigAuthorizationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void shouldAllowOnlyAdminLoginAnonymously() throws Exception {
        mockMvc.perform(post("/auth/admin/login"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/auth/admin/admins"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectNormalUserFromAllAdminResources() throws Exception {
        mockMvc.perform(get("/admin/users").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/auth/admin/admins").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAdminAndKeepPublicProfileReadable() throws Exception {
        mockMvc.perform(get("/admin/users").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/users/public-user"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/users/verification/status"))
                .andExpect(status().isUnauthorized());
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, ProbeController.class})
    static class TestConfig {

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            // 无 Authorization 头时真实过滤器会直接继续链路，适合只验证授权规则。
            return new JwtAuthenticationFilter(mock(JwtTokenUtil.class));
        }
    }

    @RestController
    static class ProbeController {

        @PostMapping("/auth/admin/login")
        void adminLogin() {
        }

        @PostMapping("/auth/admin/admins")
        void createAdmin() {
        }

        @GetMapping("/admin/users")
        void adminUsers() {
        }

        @GetMapping("/users/{id}")
        void publicUser() {
        }

        @GetMapping("/users/verification/status")
        void verificationStatus() {
        }
    }
}
