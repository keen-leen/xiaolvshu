package com.xiaolvshu.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 笔记评论响应
 */
@Data
public class PostCommentResponse {
    
    /**
     * 评论ID
     */
    private Long id;
    
    /**
     * 笔记ID
     */
    private Long postId;
    
    /**
     * 评论用户ID
     */
    private Long userId;
    
    /**
     * 父评论ID
     */
    private Long parentId;
    
    /**
     * 评论内容
     */
    private String content;
    
    /**
     * 点赞数
     */
    private Integer likeCount;
    
    /**
     * 评论时间
     */
    private LocalDateTime createdAt;
    
    // ============ 用户信息 ============
    
    /**
     * 用户昵称
     */
    private String nickname;
    
    /**
     * 用户头像
     */
    private String userAvatar;
    
    /**
     * 用户自增ID
     */
    private Long userAutoId;
    
    /**
     * 用户小旅书号
     */
    private String userDisplayId;
    
    /**
     * 用户位置
     */
    private String userLocation;
    
    /**
     * 是否认证（0-未认证，1-已认证）
     */
    private Integer verified;
    
    // ============ 统计信息 ============
    
    /**
     * 子评论数量
     */
    private Integer replyCount;
    
    /**
     * 当前用户是否已点赞
     */
    private Boolean liked;
}
