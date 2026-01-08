package com.xiaolvshu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 认证申请请求DTO
 */
@Data
public class VerificationRequest {
    /**
     * 认证类型：1-个人认证，2-企业认证
     */
    @NotNull(message = "认证类型不能为空")
    private Integer type;
    
    /**
     * 认证内容/说明
     */
    @NotBlank(message = "认证内容不能为空")
    private String content;
}
