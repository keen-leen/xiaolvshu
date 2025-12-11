package com.xiaolvshu.dto;

import lombok.Data;

import java.util.List;

/**
 * 个性标签响应DTO
 */
@Data
public class PersonalityTagResponse {
    
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
