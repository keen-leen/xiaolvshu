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
 * 审核实体
 */
@Data
@TableName("audit")
public class Audit implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 审核ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;
    
    /**
     * 审核类型：1-用户审核，2-内容审核，3-评论审核
     */
    @TableField("type")
    private Integer type;
    
    /**
     * 审核内容
     */
    @TableField("content")
    private String content;
    
    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    /**
     * 审核时间
     */
    @TableField("audit_time")
    private LocalDateTime auditTime;
    
    /**
     * 审核状态：0-待审核，1-审核通过
     */
    @TableField("status")
    private Integer status;
    
    // ============ 常量定义 ============
    
    /**
     * 审核类型：用户审核
     */
    public static final int TYPE_USER = 1;
    
    /**
     * 审核类型：内容审核
     */
    public static final int TYPE_CONTENT = 2;
    
    /**
     * 审核类型：评论审核
     */
    public static final int TYPE_COMMENT = 3;
    
    /**
     * 审核状态：待审核
     */
    public static final int STATUS_PENDING = 0;
    
    /**
     * 审核状态：审核通过
     */
    public static final int STATUS_APPROVED = 1;
}
