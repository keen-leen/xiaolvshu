package com.xiaolvshu.dto;

import lombok.Data;

/**
 * 关注状态响应DTO
 */
@Data
public class FollowStatusResponse {
    
    /**
     * 当前用户是否已关注目标用户
     */
    private Boolean isFollowing;
    
    /**
     * 目标用户是否已关注当前用户
     */
    private Boolean isFollowed;
    
    /**
     * 是否互相关注
     */
    private Boolean isMutual;
    
    /**
     * 按钮类型：follow/following/mutual
     */
    private String buttonType;
    
    public FollowStatusResponse() {}
    
    public FollowStatusResponse(Boolean isFollowing, Boolean isFollowed, String buttonType) {
        this.isFollowing = isFollowing;
        this.isFollowed = isFollowed;
        this.isMutual = isFollowing != null && isFollowed != null && isFollowing && isFollowed;
        this.buttonType = buttonType;
    }
}
