package com.xiaolvshu.service;

import com.xiaolvshu.dto.CreateCommentRequest;
import com.xiaolvshu.entity.Comment;
import com.xiaolvshu.entity.Post;
import com.xiaolvshu.entity.User;
import com.xiaolvshu.exception.BusinessException;
import com.xiaolvshu.mapper.CommentMapper;
import com.xiaolvshu.mapper.LikeMapper;
import com.xiaolvshu.mapper.NotificationMapper;
import com.xiaolvshu.mapper.PostMapper;
import com.xiaolvshu.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentServiceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRejectParentCommentFromAnotherPost() {
        Fixture fixture = fixture();
        Post post = post(20L, 10L);
        Comment parent = new Comment();
        parent.setId(30L);
        parent.setPostId(99L);
        when(fixture.postMapper.selectById(20L)).thenReturn(post);
        when(fixture.commentMapper.selectById(30L)).thenReturn(parent);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> fixture.service.createComment(request(20L, 30L)));

        assertEquals("父评论不属于当前笔记", exception.getMessage());
        verify(fixture.commentMapper, never()).insert(any(Comment.class));
        verify(fixture.postMapper, never()).adjustCommentCount(any(), anyInt());
    }

    @Test
    void shouldAdjustCommentCountAtomicallyOnCreate() {
        Fixture fixture = fixture();
        when(fixture.postMapper.selectById(20L)).thenReturn(post(20L, 10L));

        fixture.service.createComment(request(20L, null));

        verify(fixture.postMapper).adjustCommentCount(20L, 1);
        verify(fixture.postMapper, never()).updateById(any(Post.class));
    }

    @Test
    void shouldAdjustCommentCountByDeletedSubtreeSize() {
        Fixture fixture = fixture();
        Comment comment = new Comment();
        comment.setId(40L);
        comment.setPostId(20L);
        comment.setUserId(10L);
        when(fixture.commentMapper.selectById(40L)).thenReturn(comment);
        when(fixture.commentMapper.selectList(any())).thenReturn(List.of());

        fixture.service.deleteComment(40L);

        verify(fixture.postMapper).adjustCommentCount(20L, -1);
        verify(fixture.postMapper, never()).selectById(20L);
        verify(fixture.postMapper, never()).updateById(any(Post.class));
    }

    private static Fixture fixture() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        LikeMapper likeMapper = mock(LikeMapper.class);
        LikeService likeService = mock(LikeService.class);
        PostMapper postMapper = mock(PostMapper.class);
        NotificationMapper notificationMapper = mock(NotificationMapper.class);
        User current = new User();
        current.setId(10L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(current, null, List.of()));
        CommentService service = new CommentService(
                commentMapper, userMapper, likeMapper, likeService, postMapper, notificationMapper);
        return new Fixture(service, commentMapper, postMapper);
    }

    private static CreateCommentRequest request(Long postId, Long parentId) {
        CreateCommentRequest request = new CreateCommentRequest();
        request.setPostId(postId);
        request.setParentId(parentId);
        request.setContent("测试评论");
        return request;
    }

    private static Post post(Long id, Long userId) {
        Post post = new Post();
        post.setId(id);
        post.setUserId(userId);
        return post;
    }

    private record Fixture(CommentService service, CommentMapper commentMapper, PostMapper postMapper) {
    }
}
