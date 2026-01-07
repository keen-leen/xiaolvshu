package com.xiaolvshu.dto.admin;

import lombok.Data;

/**
 * 管理端评论查询请求参数
 */
@Data
public class AdminCommentQueryDTO {
    /**
     * 页码
     */
    private Integer page = 1;

    /**
     * 每页数量
     */
    private Integer limit = 20;

    /**
     * 笔记ID
     */
    private Long postId;

    /**
     * 用户显示ID（模糊搜索）
     */
    private String userDisplayId;

    /**
     * 评论内容（模糊搜索）
     */
    private String content;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序方式（ASC/DESC）
     */
    private String sortOrder;
}
