package com.xiaolvshu.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 密码工具类 - 使用 SHA-256 哈希
 */
public class PasswordUtil {
    
    /**
     * 使用 SHA-256 对密码进行哈希
     * @param password 原始密码
     * @return SHA-256 哈希后的十六进制字符串
     */
    public static String sha256(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
    
    /**
     * 验证密码是否匹配
     * @param rawPassword 原始密码
     * @param encodedPassword 数据库中存储的哈希密码
     * @return 是否匹配
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        String hashed = sha256(rawPassword);
        return hashed.equalsIgnoreCase(encodedPassword);
    }
    
    /**
     * 字节数组转十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
