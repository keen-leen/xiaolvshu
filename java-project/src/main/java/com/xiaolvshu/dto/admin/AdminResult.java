package com.xiaolvshu.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理端统一响应结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminResult<T> implements Serializable {
    
    private Integer code;
    private String message;
    private T data;

    @Data
    public static class Pagination {
        private Long total;
        private Integer page;
        private Integer limit;
        private Long pages;
    }
    
    /**
     * 成功响应（无数据）
     */
    public static <T> AdminResult<T> success(String message) {
        AdminResult<T> result = new AdminResult<>();
        result.setCode(200);
        result.setMessage(message);
        return result;
    }
    
    /**
     * 成功响应（有数据）
     */
    public static <T> AdminResult<T> success(String message, T data) {
        AdminResult<T> result = new AdminResult<>();
        result.setCode(200);
        result.setMessage(message);
        result.setData(data);
        return result;
    }
    
    /**
     * 分页成功响应
     * 返回格式: { code: 200, message: "获取成功", data: { data: [...], pagination: {...} } }
     */
    public static <T> AdminResult<Map<String, Object>> success(T data, Long total, Integer page, Integer limit) {
        AdminResult<Map<String, Object>> result = new AdminResult<>();
        result.setCode(200);
        result.setMessage("获取成功");
        
        Pagination pagination = new Pagination();
        pagination.setTotal(total);
        pagination.setPage(page);
        pagination.setLimit(limit);
        pagination.setPages((total + limit - 1) / limit);
        
        Map<String, Object> dataWrapper = new HashMap<>();
        dataWrapper.put("data", data);
        dataWrapper.put("pagination", pagination);
        result.setData(dataWrapper);
        
        return result;
    }
    
    /**
     * 错误响应
     */
    public static <T> AdminResult<T> error(Integer code, String message) {
        AdminResult<T> result = new AdminResult<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
    
    /**
     * 404错误
     */
    public static <T> AdminResult<T> notFound(String message) {
        return error(404, message);
    }
    
    /**
     * 400错误
     */
    public static <T> AdminResult<T> badRequest(String message) {
        return error(400, message);
    }
    
    /**
     * 409冲突错误
     */
    public static <T> AdminResult<T> conflict(String message) {
        return error(409, message);
    }
}
