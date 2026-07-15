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

    /**
     * 是否已完成向量化：0-未完成，1-已完成
     */
    @TableField("is_vectorized")
    private Integer isVectorized;

    /**
     * 最近一次向量化时间
     */
    @TableField("vectorized_at")
    private LocalDateTime vectorizedAt;

    /**
     * 是否已同步到 Elasticsearch 全文索引：0-未同步，1-已同步
     */
    @TableField("is_indexed")
    private Integer isIndexed;

    /**
     * 最近一次全文索引同步成功时间
     */
    @TableField("indexed_at")
    private LocalDateTime indexedAt;

    /**
     * 热度分（非数据库字段，由推荐查询动态计算）
     * 公式：(点赞×3 + 收藏×2 + 评论×2 + MIN(浏览×0.1, 50)) / (发布小时数+2)^1.5
     */
    @TableField(exist = false)
    private Double hotScore;
}
