package com.xiaolvshu.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一响应结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {
    
    private Integer code;
    private String message;
    private T data;
    
    public static <T> Result<T> success() {
        return new Result<>(ResponseCode.SUCCESS, "操作成功", null);
    }
    
    public static <T> Result<T> success(T data) {
        return new Result<>(ResponseCode.SUCCESS, "操作成功", data);
    }
    
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResponseCode.SUCCESS, message, data);
    }
    public static <T> Result<T> success(String message) {
        return new Result<>(ResponseCode.SUCCESS, message, null);
    }
    
    public static <T> Result<T> error(String message) {
        return new Result<>(ResponseCode.ERROR, message, null);
    }
    
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }
    
    public static <T> Result<T> unauthorized(String message) {
        return new Result<>(ResponseCode.UNAUTHORIZED, message, null);
    }
    
    public static <T> Result<T> forbidden(String message) {
        return new Result<>(ResponseCode.FORBIDDEN, message, null);
    }
    
    public static <T> Result<T> notFound(String message) {
        return new Result<>(ResponseCode.NOT_FOUND, message, null);
    }
}
