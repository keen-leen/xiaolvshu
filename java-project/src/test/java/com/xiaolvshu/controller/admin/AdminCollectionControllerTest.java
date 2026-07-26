package com.xiaolvshu.controller.admin;

import com.xiaolvshu.dto.admin.AdminCollectionDTO;
import com.xiaolvshu.entity.Collection;
import com.xiaolvshu.entity.Post;
import com.xiaolvshu.entity.User;
import com.xiaolvshu.service.CollectionService;
import com.xiaolvshu.service.PostService;
import com.xiaolvshu.service.UserService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminCollectionControllerTest {

    @Test
    void shouldAssembleCurrentPageWithBatchQueries() {
        CollectionService collectionService = mock(CollectionService.class);
        UserService userService = mock(UserService.class);
        PostService postService = mock(PostService.class);
        AdminCollectionController controller =
                new AdminCollectionController(collectionService, userService, postService);

        User user = new User();
        user.setId(10L);
        user.setUserId("traveller");
        user.setNickname("旅行者");
        Post firstPost = new Post();
        firstPost.setId(20L);
        firstPost.setTitle("城市漫游");
        Post secondPost = new Post();
        secondPost.setId(30L);
        secondPost.setTitle("周末路线");
        when(userService.listByIds(any(java.util.Collection.class))).thenReturn(List.of(user));
        when(postService.listByIds(any(java.util.Collection.class))).thenReturn(List.of(firstPost, secondPost));

        List<AdminCollectionDTO> result = controller.convertToDTOs(List.of(
                collection(1L, 10L, 20L),
                collection(2L, 10L, 30L)));

        assertEquals("旅行者", result.getFirst().getNickname());
        assertEquals("城市漫游", result.getFirst().getPostTitle());
        assertEquals("周末路线", result.get(1).getPostTitle());
        verify(userService).listByIds(any(java.util.Collection.class));
        verify(postService).listByIds(any(java.util.Collection.class));
        verify(userService, never()).getById(any());
        verify(postService, never()).getById(any());
    }

    private static Collection collection(Long id, Long userId, Long postId) {
        Collection collection = new Collection();
        collection.setId(id);
        collection.setUserId(userId);
        collection.setPostId(postId);
        return collection;
    }
}
