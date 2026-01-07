package com.xiaolvshu.dto.admin;

import lombok.Data;

/**
 * 管理端管理员查询请求参数
 */
@Data
public class AdminAdminQueryDTO {
    /**
     * 页码
     */
    private Integer page = 1;

    /**
     * 每页数量
     */
    private Integer limit = 20;

    /**
     * 用户名（模糊搜索）
     */
    private String username;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序方式（ASC/DESC）
     */
    private String sortOrder;
}
