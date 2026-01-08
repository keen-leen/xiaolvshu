package com.xiaolvshu.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分类响应DTO
 */
@Data
public class CategoryResponse {
    /**
     * 分类ID
     */
    private Integer id;
    
    /**
     * 分类名称
     */
    private String name;
    
    /**
     * 分类英文标题
     */
    private String categoryTitle;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 该分类下的笔记数量
     */
    private Long postCount;
}
