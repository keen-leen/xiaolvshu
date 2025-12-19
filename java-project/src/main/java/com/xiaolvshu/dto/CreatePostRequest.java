package com.xiaolvshu.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 创建帖子请求DTO
 */
@Data
public class CreatePostRequest {

    // 帖子标题
    private String title = "";
    
    // 帖子内容
    private String content = "";
    
    // 分类ID
    private Integer categoryId;

    // 图片URL列表
    private String[] images;

    // 视频信息
    private Video video;
    
    // 标签列表
    private String[] tags;

    // 是否为草稿
    private boolean isDraft;
    
    // 帖子类型（1-图片，2-视频）
    private Integer type = 1;

    @Data
    public static class Video {
        // 视频URL
        @JsonProperty("url")
        private String url;
        // 视频封面URL
        @JsonProperty("coverUrl")
        private String coverUrl;
        // 视频文件名
        @JsonProperty("name")
        private String filename;
        // 视频文件大小
        @JsonProperty("size")
        private Long size;
    }

    public void setIs_draft(boolean is_draft) {
        this.isDraft = is_draft;
    }
}
