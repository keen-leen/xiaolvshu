package com.xiaolvshu.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分类DTO
 */
@Data
public class CategoryDTO {
    
    private Long id;
    
    private String name;
    
    private String description;
    
    private String icon;
    
    private Integer sortOrder;
    
    private Boolean isActive;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
