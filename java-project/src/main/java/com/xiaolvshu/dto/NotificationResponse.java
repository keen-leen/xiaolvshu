package com.xiaolvshu.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知响应DTO
 */
@Data
public class NotificationResponse {
    
    private Long id;
    private Long userId;
    private Long senderId;
    private Integer type;
    private String title;
    private String content;
    private Long targetId;
    private Long commentId;
    private Integer isRead;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    // 发送者信息
    private Long fromUserAutoId;
    private String fromNickname;
    private String fromAvatar;
    private String fromUserId;
    private Integer fromVerified;
    
    // 帖子信息
    private String postTitle;
    private Integer postType;
    private Long postAuthorId;
    private String postImage;
    
    // 评论信息
    private String commentContent;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime commentCreatedAt;
    private Integer commentLikeCount;
    private Integer commentIsLiked;
    private String parentCommentContent;
    
    // 点赞通知特有
    private Integer targetType;
}
