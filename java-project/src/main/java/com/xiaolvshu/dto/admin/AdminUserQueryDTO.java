package com.xiaolvshu.dto.admin;

import lombok.Data;

/**
 * 管理端用户查询请求参数
 */
@Data
public class AdminUserQueryDTO {
    /**
     * 页码
     */
    private Integer page = 1;

    /**
     * 每页数量
     */
    private Integer limit = 20;

    /**
     * 用户ID（模糊搜索）
     */
    private String userId;

    /**
     * 昵称（模糊搜索）
     */
    private String nickname;

    /**
     * 地区（模糊搜索）
     */
    private String location;

    /**
     * 是否激活（0-未激活，1-已激活）
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
