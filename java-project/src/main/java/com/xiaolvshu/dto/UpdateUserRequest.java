package com.xiaolvshu.dto;

import lombok.Data;

import java.util.List;

/**
 * 更新用户资料请求DTO
 */
@Data
public class UpdateUserRequest {
    
    /**
     * 昵称
     */
    private String nickname;
    
    /**
     * 头像URL
     */
    private String avatar;
    
    /**
     * 个人简介
     */
    private String bio;
    
    /**
     * 性别
     */
    private String gender;
    
    /**
     * 星座
     */
    private String zodiacSign;
    
    /**
     * MBTI人格类型
     */
    private String mbti;
    
    /**
     * 学历
     */
    private String education;
    
    /**
     * 专业
     */
    private String major;
    
    /**
     * 兴趣爱好
     */
    private List<String> interests;
}
