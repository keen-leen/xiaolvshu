package com.xiaolvshu.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 管理端分类查询请求参数
 */
@Data
public class AdminCategoryQueryDTO {
    /**
     * 页码
     */
    private Integer page = 1;

    /**
     * 每页数量
     */
    private Integer limit = 10;

    /**
     * 分类名称（模糊搜索）
     */
    private String name;

    /**
     * 分类英文标题（模糊搜索）
     */
    @JsonProperty("category_title")
    private String categoryTitle;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序方式（ASC/DESC）
     */
    private String sortOrder;
}
