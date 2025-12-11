package com.xiaolvshu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 笔记视频实体
 */
@Data
@TableName("post_videos")
public class PostVideo implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 视频ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 笔记ID
     */
    @TableField("post_id")
    private Long postId;
    
    /**
     * 视频封面URL
     */
    @TableField("cover_url")
    private String coverUrl;
    
    /**
     * 视频URL
     */
    @TableField("video_url")
    private String videoUrl;
}
