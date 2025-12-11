package com.xiaolvshu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 认证响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    
    private UserDTO user;
    private TokensDTO tokens;
    
    /**
     * Token信息DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokensDTO {
        private String accessToken;
        private String refreshToken;
        private Integer expiresIn;
    }
}
