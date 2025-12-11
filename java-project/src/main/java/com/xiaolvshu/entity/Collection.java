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
 * 收藏实体
 */
@Data
@TableName("collections")
public class Collection implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 收藏ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;
    
    /**
     * 笔记ID
     */
    @TableField("post_id")
    private Long postId;
    
    /**
     * 收藏时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    // ============ 非数据库字段 ============
    
    /**
     * 笔记信息（关联查询）
     */
    @TableField(exist = false)
    private Post post;
}
