package com.xiaolvshu.dto.admin;

import lombok.Data;

import java.util.List;

/**
 * 批量删除请求参数
 */
@Data
public class BatchDeleteDTO {
    /**
     * 要删除的ID列表
     */
    private List<Long> ids;
}
