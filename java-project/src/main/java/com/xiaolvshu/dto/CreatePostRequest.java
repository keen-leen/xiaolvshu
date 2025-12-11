package com.xiaolvshu.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建帖子请求DTO
 */
@Data
public class CreatePostRequest {
    
    @NotBlank(message = "内容不能为空")
    private String content;
    
    private Long categoryId;
    
    private String postType = "text";
    
    private String location;
}
