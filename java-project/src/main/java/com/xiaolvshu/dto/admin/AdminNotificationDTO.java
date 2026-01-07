package com.xiaolvshu.dto.admin;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端通知DTO
 */
@Data
public class AdminNotificationDTO {
    private Long id;
    private Long userId;
    private Long senderId;
    private Integer type;
    private String title;
    private Long targetId;
    private Long commentId;
    private Integer isRead;
    private LocalDateTime createdAt;
    private String userNickname;
    private String userDisplayId;
    private String senderNickname;
    private String senderDisplayId;
}
