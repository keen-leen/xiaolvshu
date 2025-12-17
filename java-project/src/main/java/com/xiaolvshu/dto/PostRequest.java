package com.xiaolvshu.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
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
     * 小旅书号
     */
    private String userId;

    /**
     * 帖子类型
     */
    private Integer type;

    // 兼容前端下划线参数 user_id -> userId
    public void setUser_id(String user_id) {
        this.userId = user_id;
    }

    // 兼容前端下划线参数 is_draft -> isDraft
    public void setIs_draft(Integer is_draft) {
        this.isDraft = is_draft;
    }
}
