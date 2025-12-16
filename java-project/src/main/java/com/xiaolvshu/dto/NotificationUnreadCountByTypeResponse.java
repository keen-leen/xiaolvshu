package com.xiaolvshu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 按类型分组的未读通知数量响应DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationUnreadCountByTypeResponse {
    
    /**
     * 评论通知未读数
     */
    private Integer comments;
    
    /**
     * 点赞通知未读数
     */
    private Integer likes;
    
    /**
     * 收藏通知未读数
     */
    private Integer collections;
    
    /**
     * 关注通知未读数
     */
    private Integer follows;
    
    /**
     * 总未读数
     */
    private Integer total;
}
