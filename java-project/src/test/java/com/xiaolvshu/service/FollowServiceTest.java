package com.xiaolvshu.service;

import com.xiaolvshu.entity.Follow;
import com.xiaolvshu.entity.User;
import com.xiaolvshu.exception.BusinessException;
import com.xiaolvshu.mapper.FollowMapper;
import com.xiaolvshu.mapper.NotificationMapper;
import com.xiaolvshu.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FollowServiceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAdjustFollowCountersAtomically() {
        FollowMapper followMapper = mock(FollowMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        NotificationMapper notificationMapper = mock(NotificationMapper.class);
        FollowService service = new FollowService(followMapper, userMapper, notificationMapper);

        User follower = user(10L, "current");
        User target = user(20L, "target");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(follower, null, List.of()));
        when(userMapper.selectOne(any())).thenReturn(target);
        when(followMapper.selectCount(any())).thenReturn(0L);

        service.follow("target");

        verify(userMapper).adjustFollowCount(10L, 1);
        verify(userMapper).adjustFansCount(20L, 1);
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void shouldConvertConcurrentDuplicateFollowToBusinessError() {
        FollowMapper followMapper = mock(FollowMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        FollowService service = new FollowService(
                followMapper, userMapper, mock(NotificationMapper.class));
        User follower = user(10L, "current");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(follower, null, List.of()));
        when(userMapper.selectOne(any())).thenReturn(user(20L, "target"));
        when(followMapper.selectCount(any())).thenReturn(0L);
        when(followMapper.insert(any(Follow.class)))
                .thenThrow(new DuplicateKeyException("duplicate follow"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.follow("target"));

        assertEquals("已关注该用户", exception.getMessage());
        verify(userMapper, never()).adjustFollowCount(any(), anyInt());
        verify(userMapper, never()).adjustFansCount(any(), anyInt());
    }

    private static User user(Long id, String displayId) {
        User user = new User();
        user.setId(id);
        user.setUserId(displayId);
        return user;
    }
}
