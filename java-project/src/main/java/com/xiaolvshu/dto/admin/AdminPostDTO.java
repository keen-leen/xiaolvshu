package com.xiaolvshu.dto.admin;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端笔记详情DTO
 */
@Data
public class AdminPostDTO {
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private Integer type;
    private Integer categoryId;
    private String category;
    private Long viewCount;
    private Integer likeCount;
    private Integer collectCount;
    private Integer commentCount;
    private Integer isDraft;
    private LocalDateTime createdAt;
    private String nickname;
    private String userDisplayId;
    private List<String> images;
    private String videoUrl;
    private String coverUrl;
    private List<AdminTagDTO> tags;

    @Data
    public static class AdminTagDTO {
        private Integer id;
        private String name;
    }
}
