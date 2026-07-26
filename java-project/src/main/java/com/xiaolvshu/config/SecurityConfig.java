package com.xiaolvshu.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Spring Security配置
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    
    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${app.cors.allowed-headers}")
    private String allowedHeaders;

    @Value("${app.cors.allowed-methods}")
    private String allowedMethods;
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
    
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(errors -> errors
                    .authenticationEntryPoint((request, response, exception) ->
                            response.sendError(401, "Unauthorized"))
                    .accessDeniedHandler((request, response, exception) ->
                            response.sendError(403, "Forbidden")))
            .authorizeHttpRequests(auth -> auth
                /*
                 * 管理员登录是唯一允许匿名访问的管理端入口。必须把精确规则放在
                 * /auth/admin/** 之前，禁止使用 /auth/** 这种宽泛白名单，否则创建管理员、
                 * 管理员列表等接口也会被一并放行。
                 */
                .requestMatchers(HttpMethod.POST, "/auth/admin/login").permitAll()
                .requestMatchers("/auth/admin/**", "/admin/**", "/v3/api-docs/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/auth/captcha", "/auth/check-user-id", "/auth/health", "/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login", "/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, "/ai/travel/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/ai/travel/conversations/**").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/ai/travel/conversations/**").permitAll()
                // 搜索相关公开接口
                .requestMatchers(HttpMethod.GET, "/search").permitAll()
                // 分类相关公开接口
                .requestMatchers(HttpMethod.GET,"/categories/**").permitAll()
                // 帖子相关公开接口
                .requestMatchers(HttpMethod.GET, "/posts", "/posts/**").permitAll()
                // 用户相关公开接口
                // 认证申请状态包含当前用户的私有审核记录，必须先于公开用户资料规则匹配。
                .requestMatchers(HttpMethod.GET, "/users/verification/status").authenticated()
                .requestMatchers(HttpMethod.GET, "/users", "/users/**").permitAll()
                // 评论相关公开接口
                .requestMatchers(HttpMethod.GET, "/comments/{commentId}/replies").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(Arrays.asList(allowedMethods.split(",")));
        configuration.setAllowedHeaders(Arrays.asList(allowedHeaders.split(",")));
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
