package com.xiaolvshu.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 帖子响应DTO - 用于返回给前端的帖子信息
 */
@Data
public class PostResponse {
    
    // ============ 帖子基本信息 ============
    
    /**
     * 帖子ID
     */
    private Long id;
    
    /**
     * 标题
     */
    private String title;
    
    /**
     * 内容
     */
    private String content;
    
    /**
     * 分类ID
     */
    private Integer categoryId;
    
    /**
     * 分类名称
     */
    private String category;
    
    /**
     * 笔记类型：1-图片笔记，2-视频笔记
     */
    private Integer type;
    
    /**
     * 是否为草稿：1-草稿，0-已发布
     */
    private Integer isDraft;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    // ============ 统计数据 ============
    
    /**
     * 浏览量
     */
    private Long viewCount;
    
    /**
     * 点赞数
     */
    private Integer likeCount;
    
    /**
     * 收藏数
     */
    private Integer collectCount;
    
    /**
     * 评论数
     */
    private Integer commentCount;
    
    // ============ 媒体资源 ============
    
    /**
     * 图片列表
     */
    private List<String> images;
    
    /**
     * 瀑布流展示用的首张图片
     */
    private String image;
    
    /**
     * 视频URL（视频笔记）
     */
    private String videoUrl;

    /**
     * 视频封面URL（视频笔记）
     */
    private String coverUrl;
    
    // ============ 标签信息 ============
    
    /**
     * 标签列表
     */
    private List<TagDTO> tags;
    
    // ============ 作者信息 ============
    
    /**
     * 作者自增ID
     */
    private Long userId;
    
    /**
     * 作者自增ID（兼容字段）
     */
    private Long authorAutoId;
    
    /**
     * 作者小旅书号
     */
    private String authorAccount;
    
    /**
     * 作者昵称
     */
    private String nickname;
    
    /**
     * 作者头像
     */
    private String userAvatar;
    
    /**
     * 作者IP属地
     */
    private String location;
    
    /**
     * 作者认证状态
     */
    private Integer verified;
    
    // ============ 当前用户状态 ============
    
    /**
     * 当前用户是否已点赞
     */
    private Boolean liked;
    
    /**
     * 当前用户是否已收藏
     */
    private Boolean collected;
}
