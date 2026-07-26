package com.xiaolvshu.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
@TableName("users")
public class User implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 用户ID（自增）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 密码
     */
    @TableField("password")
    private String password;
    
    /**
     * 小旅书号
     */
    @TableField("user_id")
    private String userId;
    
    /**
     * 昵称
     */
    @TableField("nickname")
    private String nickname;
    
    /**
     * 头像URL
     */
    @TableField("avatar")
    private String avatar;

    /** 演示头像对应的 Pexels 图片详情页；普通用户上传头像时为空。 */
    @TableField("avatar_source_url")
    private String avatarSourceUrl;

    /** 演示头像摄影师；普通用户上传头像时为空。 */
    @TableField("avatar_photographer")
    private String avatarPhotographer;

    /** 演示头像摄影师的 Pexels 主页。 */
    @TableField("avatar_photographer_url")
    private String avatarPhotographerUrl;
    
    /**
     * 个人简介
     */
    @TableField("bio")
    private String bio;
    
    /**
     * IP属地
     */
    @TableField("location")
    private String location;
    
    /**
     * 关注数
     */
    @TableField("follow_count")
    private Integer followCount;
    
    /**
     * 粉丝数
     */
    @TableField("fans_count")
    private Integer fansCount;
    
    /**
     * 获赞数
     */
    @TableField("like_count")
    private Integer likeCount;

    /**
     * 帖子数
     */
    @TableField("post_count")
    private Integer postCount;
    
    /**
     * 是否激活
     */
    @TableField("is_active")
    private Integer isActive;
    
    /**
     * 最后登录时间
     */
    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;
    
    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    /**
     * 性别
     */
    @TableField("gender")
    private String gender;
    
    /**
     * 星座
     */
    @TableField("zodiac_sign")
    private String zodiacSign;
    
    /**
     * MBTI人格类型
     */
    @TableField("mbti")
    private String mbti;
    
    /**
     * 学历
     */
    @TableField("education")
    private String education;
    
    /**
     * 专业
     */
    @TableField("major")
    private String major;
    
    /**
     * 兴趣爱好（JSON数组）
     */
    @TableField("interests")
    private String interests;
    
    /**
     * 认证状态：0-未认证，1-已认证
     */
    @TableField("verified")
    private Integer verified;
}
