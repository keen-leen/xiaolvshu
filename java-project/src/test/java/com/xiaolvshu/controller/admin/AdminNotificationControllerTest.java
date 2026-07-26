package com.xiaolvshu.controller.admin;

import com.xiaolvshu.dto.admin.AdminNotificationDTO;
import com.xiaolvshu.entity.Notification;
import com.xiaolvshu.entity.User;
import com.xiaolvshu.service.NotificationService;
import com.xiaolvshu.service.UserService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminNotificationControllerTest {

    @Test
    void shouldLoadReceiversAndSendersInOneBatch() {
        NotificationService notificationService = mock(NotificationService.class);
        UserService userService = mock(UserService.class);
        AdminNotificationController controller =
                new AdminNotificationController(notificationService, userService);

        User receiver = user(10L, "receiver", "接收者");
        User sender = user(20L, "sender", "发送者");
        when(userService.listByIds(any(java.util.Collection.class))).thenReturn(List.of(receiver, sender));

        Notification notification = new Notification();
        notification.setId(1L);
        notification.setUserId(10L);
        notification.setSenderId(20L);
        notification.setTitle("收藏了你的笔记");

        List<AdminNotificationDTO> result = controller.convertToDTOs(List.of(notification));

        assertEquals("接收者", result.getFirst().getUserNickname());
        assertEquals("发送者", result.getFirst().getSenderNickname());
        verify(userService).listByIds(any(java.util.Collection.class));
        verify(userService, never()).getById(any());
    }

    private static User user(Long id, String displayId, String nickname) {
        User user = new User();
        user.setId(id);
        user.setUserId(displayId);
        user.setNickname(nickname);
        return user;
    }
}
