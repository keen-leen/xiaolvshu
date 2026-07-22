package com.xiaolvshu.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.xiaolvshu.entity.Post;
import com.xiaolvshu.entity.PostTag;
import com.xiaolvshu.entity.User;
import com.xiaolvshu.mapper.PostMapper;
import com.xiaolvshu.mapper.PostTagMapper;
import com.xiaolvshu.mapper.TagMapper;
import com.xiaolvshu.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchIndexServiceTest {

    private PostMapper postMapper;
    private UserMapper userMapper;
    private SearchIndexService service;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("com.xiaolvshu.mapper.PostMapper");
        TableInfoHelper.initTableInfo(assistant, Post.class);
        TableInfoHelper.initTableInfo(assistant, PostTag.class);
        postMapper = mock(PostMapper.class);
        PostTagMapper postTagMapper = mock(PostTagMapper.class);
        TagMapper tagMapper = mock(TagMapper.class);
        userMapper = mock(UserMapper.class);
        service = spy(new SearchIndexService(null, postMapper, postTagMapper, tagMapper, userMapper));
        doNothing().when(service).ensureIndex();
    }

    @Test
    void shouldMarkPostIndexedOnlyAfterElasticsearchWriteSucceeds() {
        Post post = publishedPost();
        User user = new User();
        user.setId(9L);
        user.setNickname("测试用户");
        user.setUserId("traveler");
        when(postMapper.selectList(any(Wrapper.class))).thenReturn(List.of(post));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(user));
        doNothing().when(service).writePost(any(Post.class), any(User.class), any());

        assertEquals(1, service.syncPendingPosts());

        verify(service).writePost(any(Post.class), any(User.class), any());
        verify(postMapper).update(isNull(), any());
    }

    @Test
    void shouldLeavePostPendingWhenElasticsearchWriteFails() {
        Post post = publishedPost();
        User user = new User();
        user.setId(9L);
        when(postMapper.selectList(any(Wrapper.class))).thenReturn(List.of(post));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(user));
        doThrow(new IllegalStateException("ES unavailable"))
                .when(service).writePost(any(Post.class), any(User.class), any());

        assertThrows(IllegalStateException.class, () -> service.syncPendingPosts());

        verify(postMapper, never()).update(isNull(), any());
    }

    private Post publishedPost() {
        Post post = new Post();
        post.setId(1L);
        post.setUserId(9L);
        post.setTitle("成都攻略");
        post.setContent("测试正文");
        post.setType(1);
        post.setIsDraft(0);
        post.setIsIndexed(0);
        post.setUpdatedAt(LocalDateTime.of(2026, 7, 14, 20, 0));
        return post;
    }
}
