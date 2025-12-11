package com.xiaolvshu.dto;

import lombok.Data;

/**
 * 分页请求参数
 */
@Data
public class PageRequest {
    
    /**
     * 页码，从0开始
     */
    private Integer page = 1;
    
    /**
     * 每页数量
     */
    private Integer limit = 20;

    /**
     * 排序方式：desc（降序）或 asc（升序）
     */
    private String sort = "desc";
}
