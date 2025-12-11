package com.xiaolvshu.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaolvshu.entity.Notification;
import com.xiaolvshu.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 通知服务
 */
@Service
@RequiredArgsConstructor
public class NotificationService extends ServiceImpl<NotificationMapper, Notification> {
}
