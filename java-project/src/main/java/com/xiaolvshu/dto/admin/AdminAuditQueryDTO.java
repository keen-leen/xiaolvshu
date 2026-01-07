package com.xiaolvshu.dto.admin;

import lombok.Data;

/**
 * 管理端审核查询请求参数
 */
@Data
public class AdminAuditQueryDTO {
    /**
     * 页码
     */
    private Integer page = 1;

    /**
     * 每页数量
     */
    private Integer limit = 10;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户显示ID（模糊搜索）
     */
    private String userDisplayId;

    /**
     * 审核类型
     */
    private Integer type;

    /**
     * 审核状态
     */
    private Integer status;

    /**
     * 排序字段
     */
    private String sortBy = "created_at";

    /**
     * 排序方式
     */
    private String sortOrder = "DESC";
}
