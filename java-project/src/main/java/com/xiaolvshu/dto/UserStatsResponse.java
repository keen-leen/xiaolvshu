package com.xiaolvshu.dto;

import lombok.Data;

/**
 * 用户统计数据响应DTO
 */
@Data
public class UserStatsResponse {
    
    /**
     * 帖子数
     */
    private Long postCount;
    
    /**
     * 关注数
     */
    private Integer followCount;
    
    /**
     * 粉丝数
     */
    private Integer fansCount;
    
    /**
     * 获赞数
     */
    private Integer likeCount;
    
    /**
     * 收藏数
     */
    private Long collectCount;
    
    /**
     * 总浏览量
     */
    private Long likesAndCollects;
}
