package com.xiaolvshu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统统计响应DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SystemStatsResponse {
    /**
     * 用户总数
     */
    private Long users;
    
    /**
     * 笔记总数
     */
    private Long posts;
    
    /**
     * 评论总数
     */
    private Long comments;
    
    /**
     * 点赞总数
     */
    private Long likes;
}
