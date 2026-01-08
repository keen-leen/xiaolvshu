package com.xiaolvshu.dto;

import lombok.Data;

/**
 * 分类查询请求DTO
 */
@Data
public class CategoryQueryRequest {
    /**
     * 分类名称（模糊搜索）
     */
    private String name;
    
    /**
     * 分类英文标题（模糊搜索）
     */
    private String categoryTitle;
    
    /**
     * 排序字段：id, name, created_at, post_count
     */
    private String sortField = "id";
    
    /**
     * 排序方式：asc, desc
     */
    private String sortOrder = "asc";
}
