package com.xiaolvshu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaolvshu.dto.NotificationResponse;
import com.xiaolvshu.dto.NotificationUnreadCountByTypeResponse;
import com.xiaolvshu.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 通知Mapper
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /**
     * 获取按类型分组的未读通知数量
     *
     * @param userId 用户ID
     * @return 分组未读数量
     */
    @Select("SELECT " +
            "IFNULL(SUM(CASE WHEN type IN (4, 5, 7, 8) THEN 1 ELSE 0 END), 0) as comments, " +
            "IFNULL(SUM(CASE WHEN type IN (1, 2) THEN 1 ELSE 0 END), 0) as likes, " +
            "IFNULL(SUM(CASE WHEN type = 3 THEN 1 ELSE 0 END), 0) as collections, " +
            "IFNULL(SUM(CASE WHEN type = 6 THEN 1 ELSE 0 END), 0) as follows, " +
            "COUNT(*) as total " +
            "FROM notifications " +
            "WHERE user_id = #{userId} AND is_read = 0")
    NotificationUnreadCountByTypeResponse selectUnreadCountByType(Long userId);

    /**
     * 获取评论通知
     */
    @Select("SELECT n.*, " +
            "u.id as from_user_auto_id, u.nickname as from_nickname, u.avatar as from_avatar, " +
            "u.user_id as from_user_id, u.verified as from_verified, " +
            "p.title as post_title, p.type as post_type, p.user_id as post_author_id, " +
            "(CASE WHEN p.type = 2 THEN (SELECT cover_url FROM post_videos pv WHERE pv.post_id = p.id ORDER BY pv.id LIMIT 1) " +
            "ELSE (SELECT image_url FROM post_images pi WHERE pi.post_id = p.id ORDER BY pi.id LIMIT 1) END) as post_image, " +
            "c.content as comment_content, c.created_at as comment_created_at, c.like_count as comment_like_count, " +
            "(CASE WHEN n.comment_id IS NOT NULL THEN " +
            "(CASE WHEN EXISTS(SELECT 1 FROM likes l WHERE l.user_id = #{userId} AND l.target_type = 2 AND l.target_id = n.comment_id) THEN 1 ELSE 0 END) " +
            "ELSE 0 END) as comment_is_liked, " +
            "(CASE WHEN n.type = 5 AND c.parent_id IS NOT NULL THEN (SELECT content FROM comments WHERE id = c.parent_id) ELSE NULL END) as parent_comment_content " +
            "FROM notifications n " +
            "LEFT JOIN users u ON n.sender_id = u.id " +
            "LEFT JOIN posts p ON n.target_id = p.id " +
            "LEFT JOIN comments c ON n.comment_id = c.id " +
            "WHERE n.user_id = #{userId} AND n.type IN (4, 5, 7, 8) " +
            "ORDER BY n.created_at DESC")
    IPage<NotificationResponse> selectCommentNotifications(Page<NotificationResponse> page, @Param("userId") Long userId);

    /**
     * 获取点赞通知
     */
    @Select("SELECT n.*, " +
            "u.id as from_user_auto_id, u.nickname as from_nickname, u.avatar as from_avatar, " +
            "u.user_id as from_user_id, u.verified as from_verified, " +
            "p.title as post_title, p.type as post_type, p.user_id as post_author_id, " +
            "(CASE WHEN p.type = 2 THEN (SELECT cover_url FROM post_videos pv WHERE pv.post_id = p.id ORDER BY pv.id LIMIT 1) " +
            "ELSE (SELECT image_url FROM post_images pi WHERE pi.post_id = p.id ORDER BY pi.id LIMIT 1) END) as post_image, " +
            "(CASE WHEN n.type = 1 THEN 1 WHEN n.type = 2 THEN 2 ELSE 1 END) as target_type, " +
            "(CASE WHEN n.type = 2 THEN n.comment_id ELSE NULL END) as comment_id " +
            "FROM notifications n " +
            "LEFT JOIN users u ON n.sender_id = u.id " +
            "LEFT JOIN posts p ON n.target_id = p.id " +
            "WHERE n.user_id = #{userId} AND n.type IN (1, 2) " +
            "ORDER BY n.created_at DESC")
    IPage<NotificationResponse> selectLikeNotifications(Page<NotificationResponse> page, @Param("userId") Long userId);

    /**
     * 获取关注通知
     */
    @Select("SELECT n.*, " +
            "u.id as from_user_auto_id, u.nickname as from_nickname, u.avatar as from_avatar, " +
            "u.user_id as from_user_id, u.verified as from_verified " +
            "FROM notifications n " +
            "LEFT JOIN users u ON n.sender_id = u.id " +
            "WHERE n.user_id = #{userId} AND n.type = 6 " +
            "ORDER BY n.created_at DESC")
    IPage<NotificationResponse> selectFollowNotifications(Page<NotificationResponse> page, @Param("userId") Long userId);

    /**
     * 获取收藏通知
     */
    @Select("SELECT n.*, " +
            "u.id as from_user_auto_id, u.nickname as from_nickname, u.avatar as from_avatar, " +
            "u.user_id as from_user_id, u.verified as from_verified, " +
            "p.title as post_title, p.type as post_type, " +
            "(CASE WHEN p.type = 2 THEN (SELECT cover_url FROM post_videos pv WHERE pv.post_id = p.id ORDER BY pv.id LIMIT 1) " +
            "ELSE (SELECT image_url FROM post_images pi WHERE pi.post_id = p.id ORDER BY pi.id LIMIT 1) END) as post_image " +
            "FROM notifications n " +
            "LEFT JOIN users u ON n.sender_id = u.id " +
            "LEFT JOIN posts p ON n.target_id = p.id " +
            "WHERE n.user_id = #{userId} AND n.type = 3 " +
            "ORDER BY n.created_at DESC")
    IPage<NotificationResponse> selectCollectionNotifications(Page<NotificationResponse> page, @Param("userId") Long userId);
}
