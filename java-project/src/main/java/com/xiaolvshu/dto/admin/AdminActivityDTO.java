package com.xiaolvshu.dto.admin;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端动态活动DTO
 */
@Data
public class AdminActivityDTO {
    private String id;
    private String type;
    private String userId;
    private String nickname;
    private String avatar;
    private String title;
    private String content;
    private String description;
    private Long targetId;
    private LocalDateTime createdAt;
}
