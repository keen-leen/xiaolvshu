package com.xiaolvshu.consumer;

import com.xiaolvshu.dto.LikeMessage;
import com.xiaolvshu.entity.Like;
import com.xiaolvshu.entity.Post;
import com.xiaolvshu.entity.Notification;
import com.xiaolvshu.entity.User;
import com.xiaolvshu.mapper.CommentMapper;
import com.xiaolvshu.mapper.LikeMapper;
import com.xiaolvshu.mapper.NotificationMapper;
import com.xiaolvshu.mapper.PostMapper;
import com.xiaolvshu.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LikeMessageConsumerTest {

    private LikeMapper likeMapper;
    private PostMapper postMapper;
    private UserMapper userMapper;
    private NotificationMapper notificationMapper;
    private LikeMessageConsumer consumer;

    @BeforeEach
    void setUp() {
        likeMapper = mock(LikeMapper.class);
        postMapper = mock(PostMapper.class);
        userMapper = mock(UserMapper.class);
        notificationMapper = mock(NotificationMapper.class);
        consumer = new LikeMessageConsumer(
                likeMapper,
                postMapper,
                userMapper,
                mock(CommentMapper.class),
                notificationMapper);
    }

    @Test
    void shouldUseAtomicCountersWhenPersistingPostLike() {
        Post post = new Post();
        post.setId(20L);
        post.setUserId(30L);
        User author = new User();
        author.setId(30L);
        when(postMapper.selectById(20L)).thenReturn(post);
        when(userMapper.selectById(30L)).thenReturn(author);

        consumer.handleLikeMessage(LikeMessage.builder()
                .userId(10L)
                .targetId(20L)
                .targetType(Like.TARGET_TYPE_POST)
                .action(LikeMessage.ACTION_LIKE)
                .build());

        verify(postMapper).adjustLikeCount(20L, 1);
        verify(userMapper).adjustLikeCount(30L, 1);
        verify(notificationMapper).insert(any(Notification.class));
    }

    @Test
    void shouldRejectUnknownActionInsteadOfAcknowledgingIt() {
        LikeMessage message = LikeMessage.builder()
                .userId(10L)
                .targetId(20L)
                .targetType(Like.TARGET_TYPE_POST)
                .action("UNKNOWN")
                .build();

        assertThrows(IllegalArgumentException.class, () -> consumer.handleLikeMessage(message));
        verify(likeMapper, never()).insert(any(Like.class));
    }
}
