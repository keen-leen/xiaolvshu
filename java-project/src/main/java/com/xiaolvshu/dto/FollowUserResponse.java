package com.xiaolvshu.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 关注/粉丝用户响应DTO
 */
@Data
public class FollowUserResponse {
    
    /**
     * 用户自增ID
     */
    private Long id;
    
    /**
     * 小旅书号
     */
    private String userId;
    
    /**
     * 昵称
     */
    private String nickname;
    
    /**
     * 头像URL
     */
    private String avatar;
    
    /**
     * 个人简介
     */
    private String bio;
    
    /**
     * 认证状态
     */
    private Integer verified;
    
    /**
     * 关注时间
     */
    private LocalDateTime followedAt;
    
    /**
     * 当前用户是否已关注该用户
     */
    private Boolean isFollowing;
    
    /**
     * 该用户是否已关注当前用户
     */
    private Boolean isFollowed;
    
    /**
     * 是否互相关注
     */
    private Boolean isMutual;
    
    /**
     * 按钮类型：follow/following/mutual
     */
    private String buttonType;
}
