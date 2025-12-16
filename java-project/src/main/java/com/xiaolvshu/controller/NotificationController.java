package com.xiaolvshu.controller;

import com.xiaolvshu.common.Result;
import com.xiaolvshu.dto.NotificationResponse;
import com.xiaolvshu.dto.NotificationUnreadCountByTypeResponse;
import com.xiaolvshu.dto.NotificationUnreadCountResponse;
import com.xiaolvshu.dto.PageRequest;
import com.xiaolvshu.dto.PageResult;
import com.xiaolvshu.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 获取未读通知数量
     * GET /notifications/unread-count
     */
    @GetMapping("/unread-count")
    public Result<NotificationUnreadCountResponse> getUnreadCount() {
        return Result.success("success", notificationService.getUnreadCount());
    }

    /**
     * 获取按类型分组的未读通知数量
     * GET /notifications/unread-count-by-type
     */
    @GetMapping("/unread-count-by-type")
    public Result<NotificationUnreadCountByTypeResponse> getUnreadCountByType() {
        return Result.success("success", notificationService.getUnreadCountByType());
    }

    /**
     * 获取评论通知
     * GET /notifications/comments
     */
    @GetMapping("/comments")
    public Result<PageResult<NotificationResponse>> getCommentNotifications(PageRequest pageRequest) {
        return Result.success("success", notificationService.getCommentNotifications(pageRequest));
    }

    /**
     * 获取点赞通知
     * GET /notifications/likes
     */
    @GetMapping("/likes")
    public Result<PageResult<NotificationResponse>> getLikeNotifications(PageRequest pageRequest) {
        return Result.success("success", notificationService.getLikeNotifications(pageRequest));
    }

    /**
     * 获取关注通知
     * GET /notifications/follows
     */
    @GetMapping("/follows")
    public Result<PageResult<NotificationResponse>> getFollowNotifications(PageRequest pageRequest) {
        return Result.success("success", notificationService.getFollowNotifications(pageRequest));
    }

    /**
     * 获取收藏通知
     * GET /notifications/collections
     */
    @GetMapping("/collections")
    public Result<PageResult<NotificationResponse>> getCollectionNotifications(PageRequest pageRequest) {
        return Result.success("success", notificationService.getCollectionNotifications(pageRequest));
    }

    /**
     * 标记通知为已读
     * PUT /notifications/:id/read
     */
    @PutMapping("/{id}/read")
    public Result<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return Result.success("标记成功");
    }

    /**
     * 标记所有通知为已读
     * PUT /notifications/read-all
     */
    @PutMapping("/read-all")
    public Result<Void> markAllAsRead() {
        notificationService.markAllAsRead();
        return Result.success("全部标记成功");
    }
}
