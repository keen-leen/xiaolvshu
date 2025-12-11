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
 * 关注关系实体
 */
@Data
@TableName("follows")
public class Follow implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 关注ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 关注者ID
     */
    @TableField("follower_id")
    private Long followerId;
    
    /**
     * 被关注者ID
     */
    @TableField("following_id")
    private Long followingId;
    
    /**
     * 关注时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    // ============ 非数据库字段 ============
    
    /**
     * 关注者信息（关联查询）
     */
    @TableField(exist = false)
    private User follower;
    
    /**
     * 被关注者信息（关联查询）
     */
    @TableField(exist = false)
    private User following;
}
