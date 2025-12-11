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
 * 评论实体
 */
@Data
@TableName("comments")
public class Comment implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 评论ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 笔记ID
     */
    @TableField("post_id")
    private Long postId;
    
    /**
     * 评论用户ID
     */
    @TableField("user_id")
    private Long userId;
    
    /**
     * 父评论ID
     */
    @TableField("parent_id")
    private Long parentId;
    
    /**
     * 评论内容
     */
    @TableField("content")
    private String content;
    
    /**
     * 点赞数
     */
    @TableField("like_count")
    private Integer likeCount;
    
    /**
     * 评论时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    // ============ 非数据库字段 ============
    
    /**
     * 笔记信息（关联查询）
     */
    @TableField(exist = false)
    private Post post;
    
    /**
     * 用户信息（关联查询）
     */
    @TableField(exist = false)
    private User user;
    
    /**
     * 子评论数量
     */
    @TableField(exist = false)
    private Integer replyCount;
    
    /**
     * 当前用户是否已点赞
     */
    @TableField(exist = false)
    private Boolean liked;
}
