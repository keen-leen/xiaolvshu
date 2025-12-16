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
 * 通知实体
 */
@Data
@TableName("notifications")
public class Notification implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 通知ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 接收用户ID
     */
    @TableField("user_id")
    private Long userId;
    
    /**
     * 发送用户ID
     */
    @TableField("sender_id")
    private Long senderId;
    
    /**
     * 通知类型: 1-点赞, 2-评论, 3-关注
     */
    @TableField("type")
    private Integer type;
    
    /**
     * 通知标题
     */
    @TableField("title")
    private String title;
    
    /**
     * 关联目标ID
     */
    @TableField("target_id")
    private Long targetId;
    
    /**
     * 关联评论ID
     */
    @TableField("comment_id")
    private Long commentId;
    
    /**
     * 是否已读
     */
    @TableField("is_read")
    private Integer isRead;
    
    /**
     * 通知时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    // ============ 常量定义 ============
    
    /**
     * 通知类型：点赞
     */
    public static final int TYPE_LIKE_POST = 1;     // 点赞笔记
    public static final int TYPE_LIKE_COMMENT = 2;  // 点赞评论
    
    /**
     * 通知类型：收藏
     */
    public static final int TYPE_COLLECT_POST = 3;  // 收藏笔记

    /**
     * 通知类型：评论
     */
    public static final int TYPE_COMMENT_POST = 4;  // 评论笔记
    public static final int TYPE_REPLY_COMMENT = 5; // 回复评论
    public static final int TYPE_MENTION_COMMENT = 7; // 评论中@提及
    public static final int TYPE_MENTION_POST = 8;    // 笔记中@提及
    
    /**
     * 通知类型：关注
     */
    public static final int TYPE_FOLLOW_USER = 6;   // 关注用户

}
