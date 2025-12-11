package com.xiaolvshu.context;

/**
 * 用户上下文 - 使用ThreadLocal存储当前登录用户信息
 */
public class UserContext {
    
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    
    /**
     * 设置当前用户ID
     */
    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }
    
    /**
     * 获取当前用户ID
     */
    public static Long getUserId() {
        return USER_ID.get();
    }
    
    /**
     * 设置当前用户名
     */
    public static void setUsername(String username) {
        USERNAME.set(username);
    }
    
    /**
     * 获取当前用户名
     */
    public static String getUsername() {
        return USERNAME.get();
    }
    
    /**
     * 清除当前用户信息
     */
    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
    }
}
