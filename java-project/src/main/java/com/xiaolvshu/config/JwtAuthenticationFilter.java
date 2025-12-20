package com.xiaolvshu.config;

import com.xiaolvshu.context.UserContext;
import com.xiaolvshu.utils.JwtTokenUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT认证过滤器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtTokenUtil jwtTokenUtil;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        try {
            // 从请求中提取JWT
            String jwt = getJwtFromRequest(request);
            // 验证JWT并设置用户信息到ThreadLocal
            if (StringUtils.hasText(jwt) && jwtTokenUtil.validateToken(jwt)) {
                Long userId = jwtTokenUtil.getUserIdFromToken(jwt);
                String username = jwtTokenUtil.getUsernameFromToken(jwt);
                
                // 设置到ThreadLocal
                UserContext.setUserId(userId);
                UserContext.setUsername(username);
                
                // 设置Spring Security认证信息
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            log.error("无法设置用户认证信息", ex);
        }
        
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 清理ThreadLocal，防止内存泄漏
            UserContext.clear();
        }
    }
    
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
