package com.xiaolvshu.service;

import com.xiaolvshu.dto.PageResult;
import com.xiaolvshu.dto.PostResponse;
import com.xiaolvshu.dto.SearchRequest;
import com.xiaolvshu.dto.SearchResponse;
import com.xiaolvshu.entity.Collection;
import com.xiaolvshu.entity.Like;
import com.xiaolvshu.entity.Post;
import com.xiaolvshu.entity.User;
import com.xiaolvshu.mapper.CollectionMapper;
import com.xiaolvshu.mapper.FollowMapper;
import com.xiaolvshu.mapper.LikeMapper;
import com.xiaolvshu.mapper.PostImageMapper;
import com.xiaolvshu.mapper.PostMapper;
import com.xiaolvshu.mapper.PostTagMapper;
import com.xiaolvshu.mapper.PostVideoMapper;
import com.xiaolvshu.mapper.TagMapper;
import com.xiaolvshu.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchServiceTest {

    private SearchService service;
    private SearchIndexService searchIndexService;

    @BeforeEach
    void setUp() {
        PostMapper postMapper = mock(PostMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        TagMapper tagMapper = mock(TagMapper.class);
        PostTagMapper postTagMapper = mock(PostTagMapper.class);
        PostImageMapper postImageMapper = mock(PostImageMapper.class);
        PostVideoMapper postVideoMapper = mock(PostVideoMapper.class);
        LikeMapper likeMapper = mock(LikeMapper.class);
        CollectionMapper collectionMapper = mock(CollectionMapper.class);
        FollowMapper followMapper = mock(FollowMapper.class);
        PostService postService = mock(PostService.class);
        searchIndexService = mock(SearchIndexService.class);

        service = new SearchService(postMapper, userMapper, tagMapper, postTagMapper,
                postImageMapper, postVideoMapper, likeMapper, collectionMapper, followMapper,
                postService, searchIndexService);

        Post first = post(1L, 9L, 0, 11, 31);
        Post second = post(2L, 9L, 0, 22, 32);
        Post draft = post(3L, 9L, 1, 33, 33);
        when(searchIndexService.searchPosts("成都", "", null, 1, 10))
                .thenReturn(new SearchIndexService.PostSearchResult(
                        List.of(2L, 4L, 1L, 3L), 4L, Map.of("美食", 2L)));
        when(postMapper.selectBatchIds(any())).thenReturn(List.of(first, draft, second));
        when(postImageMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(postVideoMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(postTagMapper.selectList(any())).thenReturn(Collections.emptyList());

        User author = new User();
        author.setId(9L);
        author.setUserId("traveler");
        author.setNickname("旅行者");
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(author));

        Like liked = new Like();
        liked.setTargetId(2L);
        when(likeMapper.selectList(any())).thenReturn(List.of(liked));
        Collection collected = new Collection();
        collected.setPostId(1L);
        when(collectionMapper.selectList(any())).thenReturn(List.of(collected));

        when(postService.convertToResponseForSearch(any(), any(), any(), any(), any(),
                anyBoolean(), anyBoolean())).thenAnswer(invocation -> {
                    Post post = invocation.getArgument(0);
                    PostResponse response = new PostResponse();
                    response.setId(post.getId());
                    response.setLikeCount(post.getLikeCount());
                    response.setCollectCount(post.getCollectCount());
                    response.setLiked(invocation.getArgument(5));
                    response.setCollected(invocation.getArgument(6));
                    return response;
                });

        User currentUser = new User();
        currentUser.setId(100L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, null, Collections.emptyList()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldUseSamePostAssemblyForBothSearchEndpointsAndFilterDrafts() {
        PageResult<PostResponse> simple = service.searchPosts("成都", 1, 10);

        SearchRequest request = new SearchRequest();
        request.setKeyword("成都");
        request.setType("all");
        request.setPage(1);
        request.setLimit(10);
        SearchResponse full = service.search(request);

        assertEquals(List.of(2L, 1L), simple.getList().stream().map(PostResponse::getId).toList());
        assertEquals(List.of(2L, 1L), full.getData().stream().map(PostResponse::getId).toList());
        assertEquals(List.of(22, 11), simple.getList().stream().map(PostResponse::getLikeCount).toList());
        assertEquals(List.of(32, 31), simple.getList().stream().map(PostResponse::getCollectCount).toList());
        assertTrue(simple.getList().get(0).getLiked());
        assertFalse(simple.getList().get(0).getCollected());
        assertFalse(simple.getList().get(1).getLiked());
        assertTrue(simple.getList().get(1).getCollected());
        assertEquals(4L, simple.getPagination().getTotal());
        assertEquals("美食", full.getTagStats().get(0).getLabel());
    }

    @Test
    void shouldKeepTagAndPostTypeFiltersForFullSearchEndpoint() {
        when(searchIndexService.searchPosts("成都", "美食", 1, 2, 5))
                .thenReturn(SearchIndexService.PostSearchResult.empty());

        SearchRequest request = new SearchRequest();
        request.setKeyword("成都");
        request.setTag("美食");
        request.setType("posts");
        request.setPage(2);
        request.setLimit(5);

        SearchResponse response = service.search(request);

        assertTrue(response.getPosts().getData().isEmpty());
        assertEquals(0L, response.getPosts().getPagination().getTotal());
        verify(searchIndexService).searchPosts("成都", "美食", 1, 2, 5);
    }

    private Post post(Long id, Long userId, Integer isDraft, Integer likeCount, Integer collectCount) {
        Post post = new Post();
        post.setId(id);
        post.setUserId(userId);
        post.setIsDraft(isDraft);
        post.setLikeCount(likeCount);
        post.setCollectCount(collectCount);
        return post;
    }
}
