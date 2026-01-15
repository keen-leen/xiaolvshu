package com.xiaolvshu.context;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 用户上下文工具类
 * 提供统一的方式获取当前登录用户的信息
 * 底层使用 Spring Security 的 SecurityContextHolder
 */
public class UserContext {
    
    /**
     * 获取当前登录用户的ID
     * 
     * @return 用户ID，如果未登录则返回null
     */
    public static Long getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long) {
            return (Long) authentication.getPrincipal();
        }
        return null;
    }
    
    /**
     * 获取当前登录用户的ID（非空版本）
     * 
     * @return 用户ID
     * @throws IllegalStateException 如果用户未登录
     */
    public static Long requireUserId() {
        Long userId = getUserId();
        if (userId == null) {
            throw new IllegalStateException("用户未登录");
        }
        return userId;
    }
    
    /**
     * 判断当前用户是否已登录
     * 
     * @return 是否已登录
     */
    public static boolean isAuthenticated() {
        return getUserId() != null;
    }
}
