package com.xiaolvshu.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 未读通知数量响应DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationUnreadCountResponse {
    
    /**
     * 未读数量
     */
    private Long count;
}
