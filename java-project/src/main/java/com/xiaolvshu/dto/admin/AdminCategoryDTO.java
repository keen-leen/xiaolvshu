package com.xiaolvshu.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端分类返回数据（包含笔记数量）
 */
@Data
public class AdminCategoryDTO {
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
    @JsonProperty("category_title")
    private String categoryTitle;

    /**
     * 创建时间
     */
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    /**
     * 笔记数量
     */
    @JsonProperty("post_count")
    private Long postCount;
}
