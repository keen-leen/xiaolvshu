package com.xiaolvshu.common;

/**
 * 响应状态码
 */
public class ResponseCode {

    public static final Integer ERROR = 1;
    public static final Integer SUCCESS = 200;
    public static final Integer VALIDATION_ERROR = 400;
    public static final Integer UNAUTHORIZED = 401;
    public static final Integer FORBIDDEN = 403;
    public static final Integer NOT_FOUND = 404;
    public static final Integer INTERNAL_ERROR = 500;
    
    private ResponseCode() {}
}
