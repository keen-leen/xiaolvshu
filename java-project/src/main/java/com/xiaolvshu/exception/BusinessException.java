package com.xiaolvshu.exception;

import lombok.Getter;

/**
 * 业务异常
 */
@Getter
public class BusinessException extends RuntimeException {
    
    private Integer code;
    
    public BusinessException(String message) {
        super(message);
        this.code = 1;
    }
    
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
