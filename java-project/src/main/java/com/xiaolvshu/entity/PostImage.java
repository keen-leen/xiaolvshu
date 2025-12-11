package com.xiaolvshu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 笔记图片实体
 */
@Data
@TableName("post_images")
public class PostImage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 图片ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 笔记ID
     */
    @TableField("post_id")
    private Long postId;
    
    /**
     * 图片URL
     */
    @TableField("image_url")
    private String imageUrl;
}
