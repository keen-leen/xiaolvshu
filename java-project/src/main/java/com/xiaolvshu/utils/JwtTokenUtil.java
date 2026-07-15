package com.xiaolvshu.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类
 */
@Component
public class JwtTokenUtil {

    public static final String PRINCIPAL_TYPE_USER = "USER";
    public static final String PRINCIPAL_TYPE_ADMIN = "ADMIN";
    
    @Value("${app.jwt.secret}")
    private String secret;
    
    @Value("${app.jwt.expires-in}")
    private Long expiresIn;
    
    @Value("${app.jwt.refresh-expires-in:2592000000}")
    private Long refreshExpiresIn;
    
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
    
    /**
     * 生成访问令牌（Access Token）
     */
    public String generateAccessToken(Long userId, String username) {
        return generateAccessToken(userId, username, PRINCIPAL_TYPE_USER);
    }

    /** 生成可被 Spring Security 识别为管理员的访问令牌。 */
    public String generateAdminAccessToken(Long adminId, String username) {
        return generateAccessToken(adminId, username, PRINCIPAL_TYPE_ADMIN);
    }

    private String generateAccessToken(Long userId, String username, String principalType) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("type", "access");
        claims.put("principalType", principalType);
        
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiresIn))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }
    
    /**
     * 生成刷新令牌（Refresh Token）
     */
    public String generateRefreshToken(Long userId, String username) {
        return generateRefreshToken(userId, username, PRINCIPAL_TYPE_USER);
    }

    public String generateAdminRefreshToken(Long adminId, String username) {
        return generateRefreshToken(adminId, username, PRINCIPAL_TYPE_ADMIN);
    }

    private String generateRefreshToken(Long userId, String username, String principalType) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("type", "refresh");
        claims.put("principalType", principalType);
        
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshExpiresIn))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }
    
    /**
     * 获取访问令牌过期时间（秒）
     */
    public Integer getExpiresInSeconds() {
        return (int) (expiresIn / 1000);
    }
    
    /**
     * 兼容旧方法
     */
    public String generateToken(Long userId, String username) {
        return generateAccessToken(userId, username);
    }
    
    /**
     * 从令牌中获取用户名(userId)
     */
    public String getUsernameFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }
    
    /**
     * 从令牌中获取用户ID(id)
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("userId", Long.class);
    }

    /**
     * 获取令牌主体类型。兼容旧令牌：没有 principalType 时按普通用户处理，
     * 避免旧令牌意外获得管理员权限。
     */
    public String getPrincipalTypeFromToken(String token) {
        String principalType = getClaimsFromToken(token).get("principalType", String.class);
        return principalType == null ? PRINCIPAL_TYPE_USER : principalType;
    }

    /** 只有 type=access 的令牌可用于接口认证，刷新令牌不能当作 Bearer 令牌。 */
    public boolean isAccessToken(String token) {
        return "access".equals(getClaimsFromToken(token).get("type", String.class));
    }
    
    /**
     * 验证令牌
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 从令牌中获取Claims
     */
    private Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
