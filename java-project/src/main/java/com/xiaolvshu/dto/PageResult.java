package com.xiaolvshu.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用分页查询结果
 */
@Data
@NoArgsConstructor
public class PageResult<T> {
    
    /**
     * 数据列表
     */
    private List<T> list;
    
    /**
     * 分页信息
     */
    private PaginationDTO pagination;
    
    public PageResult(List<T> list, PaginationDTO pagination) {
        this.list = list;
        this.pagination = pagination;
    }
    
    public PageResult(List<T> list, int page, int limit, long total) {
        this.list = list;
        this.pagination = new PaginationDTO(page, limit, total);
    }
    
    /**
     * 构建空结果
     */
    public static <T> PageResult<T> empty(int page, int limit) {
        return new PageResult<>(List.of(), page, limit, 0);
    }
}
