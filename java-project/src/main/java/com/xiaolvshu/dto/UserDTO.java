package com.xiaolvshu.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户DTO（与 Express 后端返回格式保持一致）
 */
@Data
public class UserDTO {
    
    private Long id;
    private String userId;
    private String nickname;
    private String avatar;
    private String bio;
    private String location;
    private Integer followCount;
    private Integer fansCount;
    private Integer likeCount;
    private Integer isActive;
    private String gender;
    private String zodiacSign;
    private String mbti;
    private String education;
    private String major;
    private List<String> interests;
    private LocalDateTime createdAt;
}
