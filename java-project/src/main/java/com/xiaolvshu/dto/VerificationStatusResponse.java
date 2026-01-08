package com.xiaolvshu.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 认证状态响应DTO
 */
@Data
public class VerificationStatusResponse {
    /**
     * 审核ID
     */
    private Long id;
    
    /**
     * 认证类型：1-个人认证，2-企业认证
     */
    private Integer type;
    
    /**
     * 审核状态：0-待审核，1-审核通过，2-审核拒绝
     */
    private Integer status;
    
    /**
     * 提交时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 审核时间
     */
    private LocalDateTime auditTime;
}
