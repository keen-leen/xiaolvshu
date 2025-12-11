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
 * 用户会话实体
 */
@Data
@TableName("user_sessions")
public class UserSession implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 会话ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;
    
    /**
     * 访问令牌
     */
    @TableField("token")
    private String token;
    
    /**
     * 刷新令牌
     */
    @TableField("refresh_token")
    private String refreshToken;
    
    /**
     * 过期时间
     */
    @TableField("expires_at")
    private LocalDateTime expiresAt;
    
    /**
     * 用户代理
     */
    @TableField("user_agent")
    private String userAgent;
    
    /**
     * 是否激活
     */
    @TableField("is_active")
    private Integer isActive;
    
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
}
