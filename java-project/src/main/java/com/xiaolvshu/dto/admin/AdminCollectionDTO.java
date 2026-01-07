package com.xiaolvshu.dto.admin;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端收藏DTO
 */
@Data
public class AdminCollectionDTO {
    private Long id;
    private Long userId;
    private Long postId;
    private LocalDateTime createdAt;
    private String nickname;
    private String userDisplayId;
    private String postTitle;
}
