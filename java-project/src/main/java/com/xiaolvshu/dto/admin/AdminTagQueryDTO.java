package com.xiaolvshu.dto.admin;

import lombok.Data;

/**
 * 管理端标签查询请求参数
 */
@Data
public class AdminTagQueryDTO {
    /**
     * 页码
     */
    private Integer page = 1;

    /**
     * 每页数量
     */
    private Integer limit = 20;

    /**
     * 标签名称（模糊搜索）
     */
    private String name;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序方式（ASC/DESC）
     */
    private String sortOrder;
}
