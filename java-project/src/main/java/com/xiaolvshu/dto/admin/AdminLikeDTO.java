package com.xiaolvshu.dto.admin;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端点赞DTO
 */
@Data
public class AdminLikeDTO {
    private Long id;
    private Long userId;
    private Integer targetType;
    private Long targetId;
    private LocalDateTime createdAt;
    private String nickname;
    private String userDisplayId;
}
