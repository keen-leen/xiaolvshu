package com.xiaolvshu.common.constant;

/**
 * Redis Key 常量定义
 * 统一管理所有 Redis key 的前缀和命名规范
 * 
 * 命名规范：
 * 1. 使用冒号(:)分隔不同层级
 * 2. 格式：项目名:模块名:业务名:标识
 * 3. 所有 key 都应该有明确的过期时间
 */
public class RedisKeyConstant {

    /**
     * 项目前缀
     */
    public static final String PROJECT_PREFIX = "xiaolvshu:";

    // ==================== 用户模块 ====================
    
    /**
     * 用户信息缓存
     * 完整 key: xiaolvshu:user:info:{userId}
     */
    public static final String USER_INFO = PROJECT_PREFIX + "user:info:";
    
    /**
     * 用户ID映射Hash（小旅书号 -> 数据库ID）
     * Hash Key: xiaolvshu:user:id_map
     * Hash Field: userId (小旅书号)
     * Hash Value: id (数据库主键ID)
     */
    public static final String USER_ID_MAP_HASH = PROJECT_PREFIX + "user:id_map";
    
    /**
     * 用户登录 Token
     * 完整 key: xiaolvshu:user:token:{userId}
     */
    public static final String USER_TOKEN = PROJECT_PREFIX + "user:token:";
    
    /**
     * 用户刷新 Token
     * 完整 key: xiaolvshu:user:refresh_token:{userId}
     */
    public static final String USER_REFRESH_TOKEN = PROJECT_PREFIX + "user:refresh_token:";
    
    /**
     * 用户登录验证码
     * 完整 key: xiaolvshu:user:captcha:{captchaId}
     */
    public static final String USER_CAPTCHA = PROJECT_PREFIX + "user:captcha:";
    
    /**
     * 用户粉丝数
     * 完整 key: xiaolvshu:user:fans_count:{userId}
     */
    public static final String USER_FANS_COUNT = PROJECT_PREFIX + "user:fans_count:";
    
    /**
     * 用户关注数
     * 完整 key: xiaolvshu:user:following_count:{userId}
     */
    public static final String USER_FOLLOWING_COUNT = PROJECT_PREFIX + "user:following_count:";

    // ==================== 帖子模块 ====================
    
    /**
     * 帖子详情缓存
     * 完整 key: xiaolvshu:post:detail:{postId}
     */
    public static final String POST_DETAIL = PROJECT_PREFIX + "post:detail:";
    
    /**
     * 帖子浏览量
     * 完整 key: xiaolvshu:post:view_count:{postId}
     */
    public static final String POST_VIEW_COUNT = PROJECT_PREFIX + "post:view_count:";
    
    /**
     * 帖子点赞数
     * 完整 key: xiaolvshu:post:like_count:{postId}
     */
    public static final String POST_LIKE_COUNT = PROJECT_PREFIX + "post:like_count:";
    
    /**
     * 帖子收藏数
     * 完整 key: xiaolvshu:post:collect_count:{postId}
     */
    public static final String POST_COLLECT_COUNT = PROJECT_PREFIX + "post:collect_count:";
    
    /**
     * 热门帖子列表
     * 完整 key: xiaolvshu:post:hot_list
     */
    public static final String POST_HOT_LIST = PROJECT_PREFIX + "post:hot_list";
    
    /**
     * 用户点赞帖子集合
     * 完整 key: xiaolvshu:post:user_likes:{userId}
     */
    public static final String POST_USER_LIKES = PROJECT_PREFIX + "post:user_likes:";
    
    /**
     * 用户收藏帖子集合
     * 完整 key: xiaolvshu:post:user_collects:{userId}
     */
    public static final String POST_USER_COLLECTS = PROJECT_PREFIX + "post:user_collects:";

    // ==================== 评论模块 ====================
    
    /**
     * 评论点赞数
     * 完整 key: xiaolvshu:comment:like_count:{commentId}
     */
    public static final String COMMENT_LIKE_COUNT = PROJECT_PREFIX + "comment:like_count:";
    
    /**
     * 用户点赞评论集合
     * 完整 key: xiaolvshu:comment:user_likes:{userId}
     */
    public static final String COMMENT_USER_LIKES = PROJECT_PREFIX + "comment:user_likes:";

    /**
     * 用户点赞缓存初始化标记
     * 完整 key: xiaolvshu:like:user_init:{targetType}:{userId}
     */
    public static final String LIKE_USER_INIT = PROJECT_PREFIX + "like:user_init:";

    // ==================== 分类/标签模块 ====================
    
    /**
     * 分类列表缓存
     * 完整 key: xiaolvshu:category:list
     */
    public static final String CATEGORY_LIST = PROJECT_PREFIX + "category:list";
    
    /**
     * 热门标签列表
     * 完整 key: xiaolvshu:tag:hot_list
     */
    public static final String TAG_HOT_LIST = PROJECT_PREFIX + "tag:hot_list";

    // ==================== 通知模块 ====================
    
    /**
     * 用户未读通知数
     * 完整 key: xiaolvshu:notification:unread_count:{userId}
     */
    public static final String NOTIFICATION_UNREAD_COUNT = PROJECT_PREFIX + "notification:unread_count:";

    // ==================== 限流模块 ====================
    
    /**
     * 接口限流计数
     * 完整 key: xiaolvshu:rate_limit:{api}:{ip或userId}
     */
    public static final String RATE_LIMIT = PROJECT_PREFIX + "rate_limit:";
    
    /**
     * 登录失败次数限制
     * 完整 key: xiaolvshu:login:fail_count:{ip或username}
     */
    public static final String LOGIN_FAIL_COUNT = PROJECT_PREFIX + "login:fail_count:";

    // ==================== 分布式锁 ====================
    
    /**
     * 分布式锁前缀
     * 完整 key: xiaolvshu:lock:{业务标识}
     */
    public static final String LOCK = PROJECT_PREFIX + "lock:";

    private RedisKeyConstant() {
        // 私有构造函数，防止实例化
    }
}
