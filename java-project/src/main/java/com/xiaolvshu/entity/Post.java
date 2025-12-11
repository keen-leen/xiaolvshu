package com.xiaolvshu.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 帖子实体
 */
@Data
@TableName("posts")
public class Post implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 笔记ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 发布用户ID
     */
    @TableField("user_id")
    private Long userId;
    
    /**
     * 标题
     */
    @TableField("title")
    private String title;
    
    /**
     * 内容
     */
    @TableField("content")
    private String content;
    
    /**
     * 分类ID
     */
    @TableField("category_id")
    private Integer categoryId;
    
    /**
     * 笔记类型：1-图片笔记，2-视频笔记
     */
    @TableField("type")
    private Integer type;
    
    /**
     * 浏览量
     */
    @TableField("view_count")
    private Long viewCount;
    
    /**
     * 点赞数
     */
    @TableField("like_count")
    private Integer likeCount;
    
    /**
     * 收藏数
     */
    @TableField("collect_count")
    private Integer collectCount;
    
    /**
     * 评论数
     */
    @TableField("comment_count")
    private Integer commentCount;
    
    /**
     * 是否为草稿：1-草稿，0-已发布
     */
    @TableField("is_draft")
    private Integer isDraft;

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
