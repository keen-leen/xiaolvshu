package com.xiaolvshu.dto.admin;

import lombok.Data;

import java.util.List;

/**
 * 管理端创建/更新笔记请求
 */
@Data
public class AdminPostCreateDTO {
    private Long userId;
    private String title;
    private String content;
    private Integer categoryId;
    private Integer type;
    private Integer isDraft;
    private Long viewCount;
    private List<String> images;
    private List<String> imageUrls;
    private List<Object> tags;
    private String videoUrl;
    private String coverUrl;
    private VideoDTO video;

    @Data
    public static class VideoDTO {
        private String url;
        private String coverUrl;
    }
}
