package com.xiaolvshu.dto.admin;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端关注DTO
 */
@Data
public class AdminFollowDTO {
    private Long id;
    private Long followerId;
    private Long followingId;
    private LocalDateTime createdAt;
    private String followerNickname;
    private String followerDisplayId;
    private String followingNickname;
    private String followingDisplayId;
}
