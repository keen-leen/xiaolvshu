package com.xiaolvshu.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PostRequest extends PageRequest {
    /**
     * 分类
     */
    private String category;

    /**
     * 是否草稿，0否 1是
     */
    private Integer isDraft = 0;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 帖子类型
     */
    private Integer type;
}
