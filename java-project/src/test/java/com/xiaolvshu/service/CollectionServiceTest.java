package com.xiaolvshu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaolvshu.context.UserContext;
import com.xiaolvshu.dto.CollectResponse;
import com.xiaolvshu.entity.Collection;
import com.xiaolvshu.entity.Notification;
import com.xiaolvshu.entity.Post;
import com.xiaolvshu.entity.User;
import com.xiaolvshu.mapper.NotificationMapper;
import com.xiaolvshu.mapper.PostMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionServiceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldInsertCompleteNotificationAndAdjustCountAtomically() {
        PostMapper postMapper = mock(PostMapper.class);
        NotificationMapper notificationMapper = mock(NotificationMapper.class);
        CollectionService service = spy(new CollectionService(postMapper, notificationMapper));

        User currentUser = new User();
        currentUser.setId(10L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, null, List.of()));

        Post post = new Post();
        post.setId(20L);
        post.setUserId(30L);
        when(postMapper.selectById(20L)).thenReturn(post);
        doReturn(null).when(service).getOne(any(LambdaQueryWrapper.class));
        doReturn(true).when(service).save(any(Collection.class));

        CollectResponse response = service.toggleCollect(20L);

        assertTrue(response.isCollected());
        verify(postMapper).adjustCollectCount(20L, 1);
        verify(postMapper, never()).updateById(any(Post.class));

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(notificationCaptor.capture());
        Notification notification = notificationCaptor.getValue();
        assertEquals("收藏了你的笔记", notification.getTitle());
        assertEquals(Notification.TYPE_COLLECT_POST, notification.getType());
        assertEquals(30L, notification.getUserId());
        assertEquals(UserContext.getUserId(), notification.getSenderId());
    }

    @Test
    void shouldCreateAdminCollectionAndAdjustCountInSameServicePath() {
        PostMapper postMapper = mock(PostMapper.class);
        CollectionService service = spy(new CollectionService(postMapper, mock(NotificationMapper.class)));
        doReturn(true).when(service).save(any(Collection.class));

        Collection created = service.createForAdmin(10L, 20L);

        assertEquals(10L, created.getUserId());
        assertEquals(20L, created.getPostId());
        verify(service).save(any(Collection.class));
        verify(postMapper).adjustCollectCount(20L, 1);
    }

    @Test
    void shouldMoveAdminCollectionCountBetweenPosts() {
        PostMapper postMapper = mock(PostMapper.class);
        CollectionService service = spy(new CollectionService(postMapper, mock(NotificationMapper.class)));
        Collection existing = new Collection();
        existing.setId(1L);
        existing.setUserId(10L);
        existing.setPostId(20L);
        doReturn(existing).when(service).getById(1L);
        doReturn(true).when(service).updateById(any(Collection.class));

        service.updatePostForAdmin(1L, 30L);

        verify(postMapper).adjustCollectCount(20L, -1);
        verify(postMapper).adjustCollectCount(30L, 1);
    }

    @Test
    void shouldBatchDeleteOnlyExistingCollectionsAndGroupCountChanges() {
        PostMapper postMapper = mock(PostMapper.class);
        CollectionService service = spy(new CollectionService(postMapper, mock(NotificationMapper.class)));
        Collection first = collection(1L, 20L);
        Collection second = collection(2L, 20L);
        Collection third = collection(3L, 30L);
        doReturn(List.of(first, second, third)).when(service).listByIds(List.of(1L, 2L, 3L, 99L));
        doReturn(true).when(service).removeByIds(List.of(1L, 2L, 3L));

        int deleted = service.deleteBatchForAdmin(List.of(1L, 2L, 3L, 99L));

        assertEquals(3, deleted);
        verify(postMapper).adjustCollectCount(20L, -2);
        verify(postMapper).adjustCollectCount(30L, -1);
    }

    private static Collection collection(Long id, Long postId) {
        Collection collection = new Collection();
        collection.setId(id);
        collection.setPostId(postId);
        return collection;
    }
}
