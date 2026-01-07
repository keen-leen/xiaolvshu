package com.xiaolvshu.dto.admin;

import lombok.Data;

/**
 * 管理端通知查询请求参数
 */
@Data
public class AdminNotificationQueryDTO {
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
     * 通知类型
     */
    private Integer type;

    /**
     * 是否已读
     */
    private Integer isRead;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序方式（ASC/DESC）
     */
    private String sortOrder;
}
