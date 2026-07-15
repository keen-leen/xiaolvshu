package com.xiaolvshu.context;

import com.xiaolvshu.entity.User;
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
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            return ((User)authentication.getPrincipal()).getId();
        }
        return null;
    }
    
    /**
     * 获取当前登录用户的账号
     */
    public static String getUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            return ((User)authentication.getPrincipal()).getUserId();
        }
        return null;
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
