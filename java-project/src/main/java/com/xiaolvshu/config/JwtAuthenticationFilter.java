package com.xiaolvshu.config;

import com.xiaolvshu.entity.User;
import com.xiaolvshu.utils.JwtTokenUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

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
            if (StringUtils.hasText(jwt) && jwtTokenUtil.validateToken(jwt) && jwtTokenUtil.isAccessToken(jwt)) {
                Long userId = jwtTokenUtil.getUserIdFromToken(jwt);
                String username = jwtTokenUtil.getUsernameFromToken(jwt);
                User principal = new User();
                principal.setId(userId);
                principal.setUserId(username);

                String principalType = jwtTokenUtil.getPrincipalTypeFromToken(jwt);
                String role = JwtTokenUtil.PRINCIPAL_TYPE_ADMIN.equals(principalType)
                        ? "ROLE_ADMIN" : "ROLE_USER";

                // 设置Spring Security认证信息
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority(role)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            log.error("无法设置用户认证信息", ex);
        }
        // 只捕获 JWT 解析异常；后续授权异常必须继续传递给 Spring Security，
        // 否则无权访问可能被吞掉而无法正确返回 401/403。
        filterChain.doFilter(request, response);
    }
    
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
