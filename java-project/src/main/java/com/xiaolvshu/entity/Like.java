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
 * 点赞实体
 */
@Data
@TableName("likes")
public class Like implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 点赞ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;
    
    /**
     * 目标类型: 1-笔记, 2-评论
     */
    @TableField("target_type")
    private Integer targetType;
    
    /**
     * 目标ID
     */
    @TableField("target_id")
    private Long targetId;
    
    /**
     * 点赞时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    // ============ 常量定义 ============
    
    /**
     * 目标类型：笔记
     */
    public static final int TARGET_TYPE_POST = 1;
    
    /**
     * 目标类型：评论
     */
    public static final int TARGET_TYPE_COMMENT = 2;
}
