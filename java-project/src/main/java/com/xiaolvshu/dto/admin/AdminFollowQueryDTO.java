package com.xiaolvshu.dto.admin;

import lombok.Data;

/**
 * 管理端关注查询请求参数
 */
@Data
public class AdminFollowQueryDTO {
    /**
     * 页码
     */
    private Integer page = 1;

    /**
     * 每页数量
     */
    private Integer limit = 20;

    /**
     * 关注者显示ID
     */
    private String followerDisplayId;

    /**
     * 被关注者显示ID
     */
    private String followingDisplayId;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序方式（ASC/DESC）
     */
    private String sortOrder;
}
