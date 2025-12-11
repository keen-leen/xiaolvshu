package com.xiaolvshu.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帖子DTO
 */
@Data
public class PostDTO {
    
    private Long id;
    private String content;
    private Long userId;
    private Long categoryId;
    private String postType;
    private Integer likesCount;
    private Integer commentsCount;
    private Integer sharesCount;
    private Integer viewsCount;
    private Boolean isPinned;
    private String location;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 关联信息
    private UserDTO user;
    private String categoryName;
}
