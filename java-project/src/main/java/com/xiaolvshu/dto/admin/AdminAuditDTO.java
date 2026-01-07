package com.xiaolvshu.dto.admin;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端审核DTO
 */
@Data
public class AdminAuditDTO {
    private Long id;
    private Long userId;
    private Integer type;
    private String content;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime auditTime;
    private String userDisplayId;
    private String nickname;
    private String avatar;
}
