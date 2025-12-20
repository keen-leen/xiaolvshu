package com.xiaolvshu.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户响应DTO - 用于返回给前端的用户信息
 */
@Data
public class UserResponse {
    
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
     * IP属地
     */
    private String location;
    
    /**
     * 关注数
     */
    private Integer followCount;
    
    /**
     * 粉丝数
     */
    private Integer fansCount;
    
    /**
     * 获赞数
     */
    private Integer likeCount;

    /**
     * 笔记数
     */
    private Integer postCount;
    
    /**
     * 性别
     */
    private String gender;
    
    /**
     * 星座
     */
    private String zodiacSign;
    
    /**
     * MBTI人格类型
     */
    private String mbti;
    
    /**
     * 学历
     */
    private String education;
    
    /**
     * 专业
     */
    private String major;
    
    /**
     * 兴趣爱好
     */
    private List<String> interests;
    
    /**
     * 认证状态：0-未认证，1-已认证
     */
    private Integer verified;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    // ============ 关注状态（可选字段）============
    
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
}
