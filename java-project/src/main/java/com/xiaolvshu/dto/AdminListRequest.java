package com.xiaolvshu.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理员列表查询请求参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminListRequest extends PageRequest {
    
    /**
     * 用户名（模糊搜索）
     */
    private String username;
    
    /**
     * 排序字段
     */
    private String sortField;
    
    /**
     * 排序方式
     */
    private String sortOrder;
}
