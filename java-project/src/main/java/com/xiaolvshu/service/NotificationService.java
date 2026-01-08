package com.xiaolvshu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaolvshu.context.UserContext;
import com.xiaolvshu.dto.NotificationResponse;
import com.xiaolvshu.dto.NotificationUnreadCountByTypeResponse;
import com.xiaolvshu.dto.NotificationUnreadCountResponse;
import com.xiaolvshu.dto.PageRequest;
import com.xiaolvshu.dto.PageResult;
import com.xiaolvshu.entity.Notification;
import com.xiaolvshu.exception.BusinessException;
import com.xiaolvshu.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 通知服务
 */
@Service
@RequiredArgsConstructor
public class NotificationService extends ServiceImpl<NotificationMapper, Notification> {

    private final NotificationMapper notificationMapper;

    /**
     * 获取未读通知数量
     *
     * @return 未读数量响应
     */
    public NotificationUnreadCountResponse getUnreadCount() {
        Long userId = UserContext.getUserId();
        long count = this.count(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
        return new NotificationUnreadCountResponse(count);
    }

    /**
     * 获取按类型分组的未读通知数量
     *
     * @return 分组未读数量响应
     */
    public NotificationUnreadCountByTypeResponse getUnreadCountByType() {
        Long userId = UserContext.getUserId();
        NotificationUnreadCountByTypeResponse response = notificationMapper.selectUnreadCountByType(userId);
        // 如果没有数据，返回全0对象
        if (response == null) {
            return new NotificationUnreadCountByTypeResponse(0, 0, 0, 0, 0);
        }
        return response;
    }

    /**
     * 获取评论通知
     */
    public PageResult<NotificationResponse> getCommentNotifications(PageRequest pageRequest) {
        Long userId = UserContext.getUserId();
        Page<NotificationResponse> pageParam = new Page<>(pageRequest.getPage(), pageRequest.getLimit());
        IPage<NotificationResponse> iPage = notificationMapper.selectCommentNotifications(pageParam, userId);
        return new PageResult<>(iPage.getRecords(), (int) iPage.getCurrent(), (int) iPage.getSize(), iPage.getTotal());
    }

    /**
     * 获取点赞通知
     */
    public PageResult<NotificationResponse> getLikeNotifications(PageRequest pageRequest) {
        Long userId = UserContext.getUserId();
        Page<NotificationResponse> pageParam = new Page<>(pageRequest.getPage(), pageRequest.getLimit());
        IPage<NotificationResponse> iPage = notificationMapper.selectLikeNotifications(pageParam, userId);
        return new PageResult<>(iPage.getRecords(), (int) iPage.getCurrent(), (int) iPage.getSize(), iPage.getTotal());
    }

    /**
     * 获取关注通知
     */
    public PageResult<NotificationResponse> getFollowNotifications(PageRequest pageRequest) {
        Long userId = UserContext.getUserId();
        Page<NotificationResponse> pageParam = new Page<>(pageRequest.getPage(), pageRequest.getLimit());
        IPage<NotificationResponse> iPage = notificationMapper.selectFollowNotifications(pageParam, userId);
        return new PageResult<>(iPage.getRecords(), (int) iPage.getCurrent(), (int) iPage.getSize(), iPage.getTotal());
    }

    /**
     * 获取收藏通知
     */
    public PageResult<NotificationResponse> getCollectionNotifications(PageRequest pageRequest) {
        Long userId = UserContext.getUserId();
        Page<NotificationResponse> pageParam = new Page<>(pageRequest.getPage(), pageRequest.getLimit());
        IPage<NotificationResponse> iPage = notificationMapper.selectCollectionNotifications(pageParam, userId);
        return new PageResult<>(iPage.getRecords(), (int) iPage.getCurrent(), (int) iPage.getSize(), iPage.getTotal());
    }

    /**
     * 标记通知为已读
     */
    public void markAsRead(Long id) {
        Long userId = UserContext.getUserId();
        // 验证通知是否属于当前用户
        Notification notification = this.getOne(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, userId));
        
        if (notification == null) {
            throw new BusinessException("通知不存在");
        }
        
        notification.setIsRead(1);
        this.updateById(notification);
    }

    /**
     * 标记所有通知为已读
     */
    public void markAllAsRead() {
        Long userId = UserContext.getUserId();
        this.update(new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
    }
    
    /**
     * 删除通知
     *
     * @param id 通知ID
     */
    public void deleteNotification(Long id) {
        Long userId = UserContext.getUserId();
        // 验证通知是否属于当前用户
        boolean removed = this.remove(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, userId));
        
        if (!removed) {
            throw new BusinessException("通知不存在");
        }
    }
}
