package com.xiaolvshu.dto;

import lombok.Data;

/**
 * 分页信息DTO
 */
@Data
public class PaginationDTO {
    
    /**
     * 当前页码
     */
    private Integer page;
    
    /**
     * 每页数量
     */
    private Integer limit;
    
    /**
     * 总记录数
     */
    private Long total;
    
    /**
     * 总页数
     */
    private Integer pages;
    
    public PaginationDTO() {}
    
    public PaginationDTO(Integer page, Integer limit, Long total) {
        this.page = page;
        this.limit = limit;
        this.total = total;
        this.pages = (int) Math.ceil((double) total / limit);
    }
}
