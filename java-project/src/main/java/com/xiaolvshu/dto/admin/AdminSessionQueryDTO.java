package com.xiaolvshu.dto.admin;

import lombok.Data;

/**
 * 管理端会话查询请求参数
 */
@Data
public class AdminSessionQueryDTO {
    /**
     * 页码
     */
    private Integer page = 1;

    /**
     * 每页数量
     */
    private Integer limit = 20;

    /**
     * 用户显示ID（模糊搜索）
     */
    private String userDisplayId;

    /**
     * 是否激活
     */
    private Integer isActive;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序方式（ASC/DESC）
     */
    private String sortOrder;
}
