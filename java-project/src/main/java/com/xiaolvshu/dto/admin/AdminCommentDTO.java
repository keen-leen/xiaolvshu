package com.xiaolvshu.dto.admin;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端评论DTO
 */
@Data
public class AdminCommentDTO {
    private Long id;
    private String content;
    private Long parentId;
    private Integer likeCount;
    private LocalDateTime createdAt;
    private Long userId;
    private String nickname;
    private String userDisplayId;
    private Long postId;
    private String postTitle;
}
