package com.xiaolvshu.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 管理端收藏查询请求参数
 */
@Data
public class AdminCollectionQueryDTO {
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
     * 笔记ID
     */
    private Long postId;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序方式（ASC/DESC）
     */
    private String sortOrder;
}
