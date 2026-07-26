package com.xiaolvshu.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.xiaolvshu.dto.admin.AdminPostDTO;
import com.xiaolvshu.entity.Category;
import com.xiaolvshu.entity.Post;
import com.xiaolvshu.entity.PostImage;
import com.xiaolvshu.entity.PostTag;
import com.xiaolvshu.entity.PostVideo;
import com.xiaolvshu.entity.Tag;
import com.xiaolvshu.entity.User;
import com.xiaolvshu.service.CategoryService;
import com.xiaolvshu.service.CollectionService;
import com.xiaolvshu.service.CommentService;
import com.xiaolvshu.service.LikeService;
import com.xiaolvshu.service.PostImageService;
import com.xiaolvshu.service.PostService;
import com.xiaolvshu.service.PostTagService;
import com.xiaolvshu.service.PostVideoService;
import com.xiaolvshu.service.TagService;
import com.xiaolvshu.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminPostControllerTest {

    @BeforeAll
    static void initializeMyBatisLambdaMetadata() {
        /*
         * 单元测试不启动 MyBatis ApplicationContext，但 LambdaQueryWrapper 仍需实体列缓存。
         * 显式初始化仅用于让测试执行到批量服务调用，不涉及真实数据库或 SQL 会话。
         */
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "admin-post-test");
        TableInfoHelper.initTableInfo(assistant, PostImage.class);
        TableInfoHelper.initTableInfo(assistant, PostVideo.class);
        TableInfoHelper.initTableInfo(assistant, PostTag.class);
    }

    @Test
    void shouldAssembleCurrentPageWithConstantNumberOfQueries() {
        PostService postService = mock(PostService.class);
        UserService userService = mock(UserService.class);
        CategoryService categoryService = mock(CategoryService.class);
        PostImageService postImageService = mock(PostImageService.class);
        PostVideoService postVideoService = mock(PostVideoService.class);
        PostTagService postTagService = mock(PostTagService.class);
        TagService tagService = mock(TagService.class);

        AdminPostController controller = new AdminPostController(
                postService,
                userService,
                categoryService,
                postImageService,
                postVideoService,
                postTagService,
                tagService,
                mock(LikeService.class),
                mock(CommentService.class),
                mock(CollectionService.class));

        Post firstPost = post(1L, 10L, 20);
        Post secondPost = post(2L, 10L, 20);
        User author = new User();
        author.setId(10L);
        author.setUserId("traveller");
        author.setNickname("旅行者");
        Category category = new Category();
        category.setId(20);
        category.setName("城市漫游");

        PostImage image = new PostImage();
        image.setPostId(1L);
        image.setImageUrl("image.jpg");
        PostVideo video = new PostVideo();
        video.setPostId(2L);
        video.setVideoUrl("video.mp4");
        PostTag postTag = new PostTag();
        postTag.setPostId(1L);
        postTag.setTagId(30);
        Tag tag = new Tag();
        tag.setId(30);
        tag.setName("周末");

        when(userService.listByIds(any(Collection.class))).thenReturn(List.of(author));
        when(categoryService.listByIds(any(Collection.class))).thenReturn(List.of(category));
        when(postImageService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(image));
        when(postVideoService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(video));
        when(postTagService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(postTag));
        when(tagService.listByIds(any(Collection.class))).thenReturn(List.of(tag));

        List<AdminPostDTO> result = controller.convertToDTOs(List.of(firstPost, secondPost));

        assertEquals("旅行者", result.getFirst().getNickname());
        assertEquals("城市漫游", result.getFirst().getCategory());
        assertEquals(List.of("image.jpg"), result.getFirst().getImages());
        assertEquals("周末", result.getFirst().getTags().getFirst().getName());
        assertEquals("video.mp4", result.get(1).getVideoUrl());

        verify(userService).listByIds(any(Collection.class));
        verify(categoryService).listByIds(any(Collection.class));
        verify(postImageService).list(any(LambdaQueryWrapper.class));
        verify(postVideoService).list(any(LambdaQueryWrapper.class));
        verify(postTagService).list(any(LambdaQueryWrapper.class));
        verify(tagService).listByIds(any(Collection.class));
        verify(userService, never()).getById(any());
        verify(categoryService, never()).getById(any());
    }

    private static Post post(Long id, Long userId, Integer categoryId) {
        Post post = new Post();
        post.setId(id);
        post.setUserId(userId);
        post.setCategoryId(categoryId);
        return post;
    }
}
