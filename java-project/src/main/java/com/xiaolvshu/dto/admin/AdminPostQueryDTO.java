package com.xiaolvshu.dto.admin;

import lombok.Data;

/**
 * 管理端笔记查询请求参数
 */
@Data
public class AdminPostQueryDTO {
    /**
     * 页码
     */
    private Integer page = 1;

    /**
     * 每页数量
     */
    private Integer limit = 20;

    /**
     * 标题（模糊搜索）
     */
    private String title;

    /**
     * 用户显示ID（模糊搜索）
     */
    private String userDisplayId;

    /**
     * 分类ID
     */
    private String categoryId;

    /**
     * 笔记类型：1-图文，2-视频
     */
    private Integer type;

    /**
     * 是否草稿：0-已发布，1-草稿
     */
    private Integer isDraft;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序方式（ASC/DESC）
     */
    private String sortOrder;
}
