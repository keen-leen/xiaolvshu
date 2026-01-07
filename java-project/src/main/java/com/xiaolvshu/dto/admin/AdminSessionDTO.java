package com.xiaolvshu.dto.admin;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端会话DTO
 */
@Data
public class AdminSessionDTO {
    private Long id;
    private Long userId;
    private String refreshToken;
    private String userAgent;
    private Integer isActive;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private String nickname;
    private String userDisplayId;
}
