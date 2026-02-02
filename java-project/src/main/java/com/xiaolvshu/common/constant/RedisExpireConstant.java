package com.xiaolvshu.common.constant;

/**
 * Redis 过期时间常量定义
 * 统一管理所有缓存的过期时间
 */
public class RedisExpireConstant {

    // ==================== 基础时间单位（秒）====================
    
    /**
     * 1分钟
     */
    public static final long ONE_MINUTE = 60L;
    
    /**
     * 5分钟
     */
    public static final long FIVE_MINUTES = 5 * 60L;
    
    /**
     * 10分钟
     */
    public static final long TEN_MINUTES = 10 * 60L;
    
    /**
     * 30分钟
     */
    public static final long THIRTY_MINUTES = 30 * 60L;
    
    /**
     * 1小时
     */
    public static final long ONE_HOUR = 60 * 60L;
    
    /**
     * 1天
     */
    public static final long ONE_DAY = 24 * 60 * 60L;
    
    /**
     * 1周
     */
    public static final long ONE_WEEK = 7 * 24 * 60 * 60L;
    
    /**
     * 1个月
     */
    public static final long ONE_MONTH = 30 * 24 * 60 * 60L;

    // ==================== 业务过期时间 ====================
    
    /**
     * 用户信息缓存过期时间：30分钟
     */
    public static final long USER_INFO_EXPIRE = THIRTY_MINUTES;
    
    /**
     * 用户 Token 过期时间：7天
     */
    public static final long USER_TOKEN_EXPIRE = ONE_WEEK;
    
    /**
     * 用户刷新 Token 过期时间：30天
     */
    public static final long USER_REFRESH_TOKEN_EXPIRE = ONE_MONTH;
    
    /**
     * 验证码过期时间：5分钟
     */
    public static final long CAPTCHA_EXPIRE = FIVE_MINUTES;
    
    /**
     * 帖子详情缓存过期时间：15分钟
     */
    public static final long POST_DETAIL_EXPIRE = TEN_MINUTES + FIVE_MINUTES;
    
    /**
     * 热门帖子列表缓存过期时间：5分钟
     */
    public static final long HOT_POST_EXPIRE = FIVE_MINUTES;
    
    /**
     * 分类列表缓存过期时间：1小时
     */
    public static final long CATEGORY_LIST_EXPIRE = ONE_HOUR;
    
    /**
     * 热门标签缓存过期时间：30分钟
     */
    public static final long HOT_TAG_EXPIRE = THIRTY_MINUTES;
    
    /**
     * 接口限流窗口时间：1分钟
     */
    public static final long RATE_LIMIT_WINDOW = ONE_MINUTE;
    
    /**
     * 登录失败锁定时间：30分钟
     */
    public static final long LOGIN_LOCK_EXPIRE = THIRTY_MINUTES;
    
    /**
     * 分布式锁默认超时时间：30秒
     */
    public static final long LOCK_DEFAULT_EXPIRE = 30L;
    
    /**
     * 计数器默认过期时间：1天
     */
    public static final long COUNTER_EXPIRE = ONE_DAY;

    private RedisExpireConstant() {
        // 私有构造函数，防止实例化
    }
}
