package com.xiaolvshu.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建评论请求
 */
@Data
public class CreateCommentRequest {
    
    /**
     * 笔记ID
     */
    @NotNull(message = "笔记ID不能为空")
    private Long postId;
    
    /**
     * 评论内容
     */
    @NotBlank(message = "评论内容不能为空")
    private String content;
    
    /**
     * 父评论ID（回复评论时使用）
     */
    private Long parentId;
}
