package com.xiaolvshu.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 推荐帖子列表请求参数
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class RecommendRequest extends PageRequest {

    /**
     * 帖子类型过滤：1-图文，2-视频，null-不限
     */
    private Integer type;
}
