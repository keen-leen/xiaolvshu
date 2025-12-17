package com.xiaolvshu.dto;

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
        private String url;
        // 视频封面URL
        private String coverUrl;
        // 视频文件名
        private String filename;
        // 视频缓冲
        private byte[] buffer;
    }

    public void setIs_draft(boolean is_draft) {
        this.isDraft = is_draft;
    }
}
