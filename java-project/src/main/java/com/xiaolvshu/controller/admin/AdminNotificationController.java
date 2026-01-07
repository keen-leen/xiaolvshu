package com.xiaolvshu.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaolvshu.dto.admin.*;
import com.xiaolvshu.entity.*;
import com.xiaolvshu.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端通知管理控制器
 */
@RestController
@RequestMapping("/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    /**
     * 分页查询通知列表
     */
    @GetMapping
    public AdminResult<?> getNotificationList(AdminNotificationQueryDTO queryDTO) {
        Page<Notification> pageParam = new Page<>(queryDTO.getPage(), queryDTO.getLimit());
        
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        
        // 通知类型搜索
        if (queryDTO.getType() != null) {
            wrapper.eq(Notification::getType, queryDTO.getType());
        }
        
        // 是否已读搜索
        if (queryDTO.getIsRead() != null) {
            wrapper.eq(Notification::getIsRead, queryDTO.getIsRead());
        }
        
        // 用户显示ID搜索
        if (queryDTO.getUserDisplayId() != null && !queryDTO.getUserDisplayId().trim().isEmpty()) {
            LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.like(User::getUserId, queryDTO.getUserDisplayId().trim());
            List<User> users = userService.list(userWrapper);
            if (users.isEmpty()) {
                return AdminResult.success(new ArrayList<>(), 0L, queryDTO.getPage(), queryDTO.getLimit());
            }
            List<Long> userIds = users.stream().map(User::getId).collect(Collectors.toList());
            wrapper.in(Notification::getUserId, userIds);
        }
        
        // 排序
        List<String> allowedSortFields = Arrays.asList("id", "created_at");
        if (queryDTO.getSortField() != null && allowedSortFields.contains(queryDTO.getSortField())) {
            boolean isAsc = "asc".equalsIgnoreCase(queryDTO.getSortOrder());
            switch (queryDTO.getSortField()) {
                case "id":
                    wrapper.orderBy(true, isAsc, Notification::getId);
                    break;
                case "created_at":
                    wrapper.orderBy(true, isAsc, Notification::getCreatedAt);
                    break;
            }
        } else {
            wrapper.orderByDesc(Notification::getCreatedAt);
        }
        
        IPage<Notification> result = notificationService.page(pageParam, wrapper);
        
        // 转换为DTO
        List<AdminNotificationDTO> notificationDTOs = new ArrayList<>();
        for (Notification notification : result.getRecords()) {
            AdminNotificationDTO dto = new AdminNotificationDTO();
            dto.setId(notification.getId());
            dto.setUserId(notification.getUserId());
            dto.setSenderId(notification.getSenderId());
            dto.setType(notification.getType());
            dto.setTitle(notification.getTitle());
            dto.setTargetId(notification.getTargetId());
            dto.setCommentId(notification.getCommentId());
            dto.setIsRead(notification.getIsRead());
            dto.setCreatedAt(notification.getCreatedAt());
            
            // 获取接收用户信息
            User user = userService.getById(notification.getUserId());
            if (user != null) {
                dto.setUserNickname(user.getNickname());
                dto.setUserDisplayId(user.getUserId() != null ? user.getUserId() : "user" + String.format("%03d", user.getId()));
            }
            
            // 获取发送用户信息
            if (notification.getSenderId() != null) {
                User sender = userService.getById(notification.getSenderId());
                if (sender != null) {
                    dto.setSenderNickname(sender.getNickname());
                    dto.setSenderDisplayId(sender.getUserId() != null ? sender.getUserId() : "user" + String.format("%03d", sender.getId()));
                }
            }
            
            notificationDTOs.add(dto);
        }
        
        return AdminResult.success(notificationDTOs, result.getTotal(), queryDTO.getPage(), queryDTO.getLimit());
    }

    /**
     * 获取单个通知详情
     */
    @GetMapping("/{id}")
    public AdminResult<Notification> getNotificationById(@PathVariable Long id) {
        Notification notification = notificationService.getById(id);
        if (notification == null) {
            return AdminResult.notFound("通知不存在");
        }
        return AdminResult.success("操作成功", notification);
    }

    /**
     * 创建通知
     */
    @PostMapping
    public AdminResult<Map<String, Long>> createNotification(@RequestBody Notification notification) {
        // 验证必填字段
        if (notification.getUserId() == null) {
            return AdminResult.badRequest("缺少必填字段: user_id");
        }
        if (notification.getSenderId() == null) {
            return AdminResult.badRequest("缺少必填字段: sender_id");
        }
        if (notification.getType() == null) {
            return AdminResult.badRequest("缺少必填字段: type");
        }
        if (notification.getTitle() == null || notification.getTitle().trim().isEmpty()) {
            return AdminResult.badRequest("缺少必填字段: title");
        }
        
        // 检查用户是否存在
        User user = userService.getById(notification.getUserId());
        if (user == null) {
            return AdminResult.badRequest("接收用户不存在");
        }
        
        User sender = userService.getById(notification.getSenderId());
        if (sender == null) {
            return AdminResult.badRequest("发送用户不存在");
        }
        
        notification.setIsRead(0);
        notificationService.save(notification);
        
        return AdminResult.success("通知创建成功", Map.of("id", notification.getId()));
    }

    /**
     * 更新通知
     */
    @PutMapping("/{id}")
    public AdminResult<Void> updateNotification(@PathVariable Long id, @RequestBody Notification notification) {
        Notification existingNotification = notificationService.getById(id);
        if (existingNotification == null) {
            return AdminResult.notFound("通知不存在");
        }
        
        if (notification.getUserId() != null) existingNotification.setUserId(notification.getUserId());
        if (notification.getSenderId() != null) existingNotification.setSenderId(notification.getSenderId());
        if (notification.getType() != null) existingNotification.setType(notification.getType());
        if (notification.getTitle() != null) existingNotification.setTitle(notification.getTitle());
        if (notification.getTargetId() != null) existingNotification.setTargetId(notification.getTargetId());
        if (notification.getCommentId() != null) existingNotification.setCommentId(notification.getCommentId());
        if (notification.getIsRead() != null) existingNotification.setIsRead(notification.getIsRead());
        
        notificationService.updateById(existingNotification);
        return AdminResult.success("通知更新成功");
    }

    /**
     * 删除单个通知
     */
    @DeleteMapping("/{id}")
    public AdminResult<Void> deleteNotification(@PathVariable Long id) {
        Notification notification = notificationService.getById(id);
        if (notification == null) {
            return AdminResult.notFound("通知不存在");
        }
        
        notificationService.removeById(id);
        return AdminResult.success("通知删除成功");
    }

    /**
     * 批量删除通知
     */
    @DeleteMapping
    public AdminResult<Map<String, Integer>> deleteNotifications(@RequestBody BatchDeleteDTO deleteDTO) {
        if (deleteDTO.getIds() == null || deleteDTO.getIds().isEmpty()) {
            return AdminResult.badRequest("缺少必填字段: ids");
        }
        
        int deletedCount = notificationService.removeByIds(deleteDTO.getIds()) ? deleteDTO.getIds().size() : 0;
        return AdminResult.success("成功删除" + deletedCount + "条通知", Map.of("deletedCount", deletedCount));
    }
}
