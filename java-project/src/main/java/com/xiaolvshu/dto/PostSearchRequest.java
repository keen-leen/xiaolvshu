package com.xiaolvshu.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 帖子搜索请求参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PostSearchRequest extends PageRequest {
    
    /**
     * 搜索关键词
     */
    private String keyword;
}
