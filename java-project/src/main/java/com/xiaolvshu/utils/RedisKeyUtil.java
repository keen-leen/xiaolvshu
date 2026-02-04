package com.xiaolvshu.utils;

import com.xiaolvshu.common.constant.RedisKeyConstant;

/**
 * Redis Key 构建工具类
 * 提供统一的 key 构建方法，保证 key 的规范性
 */
public class RedisKeyUtil {

    // ==================== 用户模块 ====================

    /**
     * 构建用户信息缓存 key
     *
     * @param username 小旅书号
     * @return key
     */
    public static String getUserInfoKey(String userId) {
        return RedisKeyConstant.USER_INFO + userId;
    }
    
    /**
     * 构建用户信息缓存 key（基于数据库ID）
     *
     * @param id 数据库ID
     * @return key
     */
    public static String getUserInfoKey(Long id) {
        return RedisKeyConstant.USER_INFO + "id:" + id;
    }
    
    /**
     * 获取用户ID映射的Hash key
     *
     * @return Hash key
     */
    public static String getUserIdMapHash() {
        return RedisKeyConstant.USER_ID_MAP_HASH;
    }

    /**
     * 构建用户 Token key
     *
     * @param userId 用户ID
     * @return key
     */
    public static String getUserTokenKey(Long userId) {
        return RedisKeyConstant.USER_TOKEN + userId;
    }

    /**
     * 构建用户刷新 Token key
     *
     * @param userId 用户ID
     * @return key
     */
    public static String getUserRefreshTokenKey(Long userId) {
        return RedisKeyConstant.USER_REFRESH_TOKEN + userId;
    }

    /**
     * 构建验证码 key
     *
     * @param captchaId 验证码ID
     * @return key
     */
    public static String getCaptchaKey(String captchaId) {
        return RedisKeyConstant.USER_CAPTCHA + captchaId;
    }

    /**
     * 构建用户粉丝数 key
     *
     * @param userId 用户ID
     * @return key
     */
    public static String getUserFansCountKey(Long userId) {
        return RedisKeyConstant.USER_FANS_COUNT + userId;
    }

    /**
     * 构建用户关注数 key
     *
     * @param userId 用户ID
     * @return key
     */
    public static String getUserFollowingCountKey(Long userId) {
        return RedisKeyConstant.USER_FOLLOWING_COUNT + userId;
    }

    // ==================== 帖子模块 ====================

    /**
     * 构建帖子详情缓存 key
     *
     * @param postId 帖子ID
     * @return key
     */
    public static String getPostDetailKey(Long postId) {
        return RedisKeyConstant.POST_DETAIL + postId;
    }

    /**
     * 构建帖子浏览量 key
     *
     * @param postId 帖子ID
     * @return key
     */
    public static String getPostViewCountKey(Long postId) {
        return RedisKeyConstant.POST_VIEW_COUNT + postId;
    }

    /**
     * 构建帖子点赞数 key
     *
     * @param postId 帖子ID
     * @return key
     */
    public static String getPostLikeCountKey(Long postId) {
        return RedisKeyConstant.POST_LIKE_COUNT + postId;
    }

    /**
     * 构建帖子收藏数 key
     *
     * @param postId 帖子ID
     * @return key
     */
    public static String getPostCollectCountKey(Long postId) {
        return RedisKeyConstant.POST_COLLECT_COUNT + postId;
    }

    /**
     * 构建热门帖子列表 key
     *
     * @return key
     */
    public static String getHotPostListKey() {
        return RedisKeyConstant.POST_HOT_LIST;
    }

    /**
     * 构建用户点赞帖子集合 key
     *
     * @param userId 用户ID
     * @return key
     */
    public static String getUserPostLikesKey(Long userId) {
        return RedisKeyConstant.POST_USER_LIKES + userId;
    }

    /**
     * 构建用户收藏帖子集合 key
     *
     * @param userId 用户ID
     * @return key
     */
    public static String getUserPostCollectsKey(Long userId) {
        return RedisKeyConstant.POST_USER_COLLECTS + userId;
    }

    // ==================== 评论模块 ====================

    /**
     * 构建评论点赞数 key
     *
     * @param commentId 评论ID
     * @return key
     */
    public static String getCommentLikeCountKey(Long commentId) {
        return RedisKeyConstant.COMMENT_LIKE_COUNT + commentId;
    }

    /**
     * 构建用户点赞评论集合 key
     *
     * @param userId 用户ID
     * @return key
     */
    public static String getUserCommentLikesKey(Long userId) {
        return RedisKeyConstant.COMMENT_USER_LIKES + userId;
    }

    // ==================== 分类/标签模块 ====================

    /**
     * 构建分类列表缓存 key
     *
     * @return key
     */
    public static String getCategoryListKey() {
        return RedisKeyConstant.CATEGORY_LIST;
    }

    /**
     * 构建热门标签列表 key
     *
     * @return key
     */
    public static String getHotTagListKey() {
        return RedisKeyConstant.TAG_HOT_LIST;
    }

    // ==================== 通知模块 ====================

    /**
     * 构建用户未读通知数 key
     *
     * @param userId 用户ID
     * @return key
     */
    public static String getNotificationUnreadCountKey(Long userId) {
        return RedisKeyConstant.NOTIFICATION_UNREAD_COUNT + userId;
    }

    // ==================== 限流模块 ====================

    /**
     * 构建接口限流 key
     *
     * @param api        接口标识
     * @param identifier 用户标识（IP或用户ID）
     * @return key
     */
    public static String getRateLimitKey(String api, String identifier) {
        return RedisKeyConstant.RATE_LIMIT + api + ":" + identifier;
    }

    /**
     * 构建登录失败次数 key
     *
     * @param identifier 标识（IP或用户名）
     * @return key
     */
    public static String getLoginFailCountKey(String identifier) {
        return RedisKeyConstant.LOGIN_FAIL_COUNT + identifier;
    }

    // ==================== 分布式锁 ====================

    /**
     * 构建分布式锁 key
     *
     * @param business 业务标识
     * @return key
     */
    public static String getLockKey(String business) {
        return RedisKeyConstant.LOCK + business;
    }

    /**
     * 构建分布式锁 key（带ID）
     *
     * @param business 业务标识
     * @param id       业务ID
     * @return key
     */
    public static String getLockKey(String business, Object id) {
        return RedisKeyConstant.LOCK + business + ":" + id;
    }

    private RedisKeyUtil() {
        // 私有构造函数，防止实例化
    }
}
