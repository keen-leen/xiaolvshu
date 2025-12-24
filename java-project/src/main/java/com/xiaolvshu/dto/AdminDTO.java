package com.xiaolvshu.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 管理员信息DTO
 */
@Data
public class AdminDTO {
    
    private Long id;
    
    private String username;
    
    private LocalDateTime createdAt;
}
