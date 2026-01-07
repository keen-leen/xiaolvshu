package com.xiaolvshu.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaolvshu.dto.admin.*;
import com.xiaolvshu.entity.*;
import com.xiaolvshu.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理后台 - 笔记管理控制器
 */
@RestController
@RequestMapping("/admin/posts")
@RequiredArgsConstructor
public class AdminPostController {

    private final PostService postService;
    private final UserService userService;
    private final CategoryService categoryService;
    private final PostImageService postImageService;
    private final PostVideoService postVideoService;
    private final PostTagService postTagService;
    private final TagService tagService;
    private final LikeService likeService;
    private final CommentService commentService;
    private final CollectionService collectionService;

    /**
     * 分页查询笔记列表
     */
    @GetMapping
    public AdminResult<?> list(AdminPostQueryDTO queryDTO) {
        // 构建查询条件
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        
        // 标题搜索
        if (queryDTO.getTitle() != null && !queryDTO.getTitle().trim().isEmpty()) {
            wrapper.like(Post::getTitle, queryDTO.getTitle().trim());
        }
        
        // 用户显示ID搜索
        if (queryDTO.getUserDisplayId() != null && !queryDTO.getUserDisplayId().trim().isEmpty()) {
            // 先查找用户
            LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.like(User::getUserId, queryDTO.getUserDisplayId().trim());
            List<User> users = userService.list(userWrapper);
            if (!users.isEmpty()) {
                List<Long> userIds = users.stream().map(User::getId).collect(Collectors.toList());
                wrapper.in(Post::getUserId, userIds);
            } else {
                // 无匹配用户，返回空结果
                wrapper.eq(Post::getId, -1);
            }
        }
        
        // 分类ID过滤
        if (queryDTO.getCategoryId() != null && !queryDTO.getCategoryId().trim().isEmpty()) {
            try {
                Integer categoryId = Integer.parseInt(queryDTO.getCategoryId());
                wrapper.eq(Post::getCategoryId, categoryId);
            } catch (NumberFormatException ignored) {
            }
        }
        
        // 笔记类型过滤
        if (queryDTO.getType() != null) {
            wrapper.eq(Post::getType, queryDTO.getType());
        }
        
        // 是否草稿过滤
        if (queryDTO.getIsDraft() != null) {
            wrapper.eq(Post::getIsDraft, queryDTO.getIsDraft());
        }
        
        // 排序
        List<String> allowedSortFields = Arrays.asList("id", "view_count", "like_count", "comment_count", "created_at");
        if (queryDTO.getSortField() != null && allowedSortFields.contains(queryDTO.getSortField())) {
            boolean isAsc = "asc".equalsIgnoreCase(queryDTO.getSortOrder());
            switch (queryDTO.getSortField()) {
                case "id":
                    wrapper.orderBy(true, isAsc, Post::getId);
                    break;
                case "view_count":
                    wrapper.orderBy(true, isAsc, Post::getViewCount);
                    break;
                case "like_count":
                    wrapper.orderBy(true, isAsc, Post::getLikeCount);
                    break;
                case "comment_count":
                    wrapper.orderBy(true, isAsc, Post::getCommentCount);
                    break;
                case "created_at":
                    wrapper.orderBy(true, isAsc, Post::getCreatedAt);
                    break;
            }
        } else {
            // 默认按创建时间倒序
            wrapper.orderByDesc(Post::getCreatedAt);
        }
        
        // 分页查询
        Page<Post> pageParam = new Page<>(queryDTO.getPage(), queryDTO.getLimit());
        IPage<Post> postPage = postService.page(pageParam, wrapper);
        
        // 转换为DTO
        List<AdminPostDTO> postDTOs = postPage.getRecords().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        
        return AdminResult.success(postDTOs, postPage.getTotal(), queryDTO.getPage(), queryDTO.getLimit());
    }

    /**
     * 获取笔记详情
     */
    @GetMapping("/{id}")
    public AdminResult<AdminPostDTO> getById(@PathVariable Long id) {
        Post post = postService.getById(id);
        if (post == null) {
            return AdminResult.notFound("笔记不存在");
        }
        return AdminResult.success("操作成功", convertToDTO(post));
    }

    /**
     * 创建笔记
     */
    @PostMapping
    public AdminResult<Map<String, Long>> create(@RequestBody AdminPostCreateDTO createDTO) {
        // 验证必填字段
        if (createDTO.getUserId() == null) {
            return AdminResult.badRequest("缺少必填字段: userId");
        }
        if (createDTO.getTitle() == null || createDTO.getTitle().trim().isEmpty()) {
            return AdminResult.badRequest("缺少必填字段: title");
        }
        
        Post post = new Post();
        post.setUserId(createDTO.getUserId());
        post.setTitle(createDTO.getTitle());
        post.setContent(createDTO.getContent());
        post.setCategoryId(createDTO.getCategoryId());
        post.setType(createDTO.getType() != null ? createDTO.getType() : 1);
        post.setIsDraft(createDTO.getIsDraft() != null ? createDTO.getIsDraft() : 0);
        post.setViewCount(createDTO.getViewCount() != null ? createDTO.getViewCount() : 0L);
        post.setLikeCount(0);
        post.setCollectCount(0);
        post.setCommentCount(0);
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        
        postService.save(post);
        
        // 更新分类的笔记数（仅非草稿时）
        if (post.getIsDraft() == null || post.getIsDraft() == 0) {
            if (post.getCategoryId() != null) {
                Category category = categoryService.getById(post.getCategoryId());
                if (category != null) {
                    category.setPostCount(category.getPostCount() == null ? 1L : category.getPostCount() + 1);
                    categoryService.updateById(category);
                }
            }
            // 更新用户的笔记数
            User user = userService.getById(post.getUserId());
            if (user != null) {
                user.setPostCount(user.getPostCount() == null ? 1 : user.getPostCount() + 1);
                userService.updateById(user);
            }
        }
        
        // 处理图片
        List<String> images = createDTO.getImages();
        if (images == null) {
            images = createDTO.getImageUrls();
        }
        if (images != null && !images.isEmpty()) {
            for (String imageUrl : images) {
                PostImage postImage = new PostImage();
                postImage.setPostId(post.getId());
                postImage.setImageUrl(imageUrl);
                postImageService.save(postImage);
            }
        }
        
        // 处理视频
        if (createDTO.getVideo() != null) {
            PostVideo postVideo = new PostVideo();
            postVideo.setPostId(post.getId());
            postVideo.setVideoUrl(createDTO.getVideo().getUrl());
            postVideo.setCoverUrl(createDTO.getVideo().getCoverUrl());
            postVideoService.save(postVideo);
        } else if (createDTO.getVideoUrl() != null) {
            PostVideo postVideo = new PostVideo();
            postVideo.setPostId(post.getId());
            postVideo.setVideoUrl(createDTO.getVideoUrl());
            postVideo.setCoverUrl(createDTO.getCoverUrl());
            postVideoService.save(postVideo);
        }
        
        // 处理标签
        if (createDTO.getTags() != null && !createDTO.getTags().isEmpty()) {
            for (Object tagObj : createDTO.getTags()) {
                Integer tagId = null;
                if (tagObj instanceof Integer) {
                    tagId = (Integer) tagObj;
                } else if (tagObj instanceof Number) {
                    tagId = ((Number) tagObj).intValue();
                } else if (tagObj instanceof String) {
                    try {
                        tagId = Integer.parseInt((String) tagObj);
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (tagId != null) {
                    PostTag postTag = new PostTag();
                    postTag.setPostId(post.getId());
                    postTag.setTagId(tagId);
                    postTagService.save(postTag);
                    // 增加标签使用计数
                    tagService.incrementUseCount(tagId);
                }
            }
        }
        
        return AdminResult.success("笔记创建成功", Map.of("id", post.getId()));
    }

    /**
     * 更新笔记
     */
    @PutMapping("/{id}")
    public AdminResult<Void> update(@PathVariable Long id, @RequestBody AdminPostCreateDTO updateDTO) {
        Post existingPost = postService.getById(id);
        if (existingPost == null) {
            return AdminResult.notFound("笔记不存在");
        }
        
        // 保存原始值用于计数更新
        Integer originalCategoryId = existingPost.getCategoryId();
        Integer originalIsDraft = existingPost.getIsDraft();
        
        // 更新基本字段
        if (updateDTO.getTitle() != null) {
            existingPost.setTitle(updateDTO.getTitle());
        }
        if (updateDTO.getContent() != null) {
            existingPost.setContent(updateDTO.getContent());
        }
        if (updateDTO.getCategoryId() != null) {
            existingPost.setCategoryId(updateDTO.getCategoryId());
        }
        if (updateDTO.getType() != null) {
            existingPost.setType(updateDTO.getType());
        }
        if (updateDTO.getIsDraft() != null) {
            existingPost.setIsDraft(updateDTO.getIsDraft());
        }
        if (updateDTO.getViewCount() != null) {
            existingPost.setViewCount(updateDTO.getViewCount());
        }
        existingPost.setUpdatedAt(LocalDateTime.now());
        
        postService.updateById(existingPost);
        
        // 处理分类和草稿状态变化的计数更新
        boolean wasPublished = originalIsDraft == null || originalIsDraft == 0;
        boolean isPublished = existingPost.getIsDraft() == null || existingPost.getIsDraft() == 0;
        boolean categoryChanged = updateDTO.getCategoryId() != null && 
                                  !java.util.Objects.equals(originalCategoryId, existingPost.getCategoryId());
        
        if (wasPublished && isPublished && categoryChanged) {
            // 发布状态下分类变化：旧分类减1，新分类加1
            if (originalCategoryId != null) {
                Category oldCategory = categoryService.getById(originalCategoryId);
                if (oldCategory != null && oldCategory.getPostCount() != null && oldCategory.getPostCount() > 0) {
                    oldCategory.setPostCount(oldCategory.getPostCount() - 1);
                    categoryService.updateById(oldCategory);
                }
            }
            if (existingPost.getCategoryId() != null) {
                Category newCategory = categoryService.getById(existingPost.getCategoryId());
                if (newCategory != null) {
                    newCategory.setPostCount(newCategory.getPostCount() == null ? 1L : newCategory.getPostCount() + 1);
                    categoryService.updateById(newCategory);
                }
            }
        } else if (!wasPublished && isPublished) {
            // 草稿 -> 发布：增加新分类和用户计数
            if (existingPost.getCategoryId() != null) {
                Category category = categoryService.getById(existingPost.getCategoryId());
                if (category != null) {
                    category.setPostCount(category.getPostCount() == null ? 1L : category.getPostCount() + 1);
                    categoryService.updateById(category);
                }
            }
            User user = userService.getById(existingPost.getUserId());
            if (user != null) {
                user.setPostCount(user.getPostCount() == null ? 1 : user.getPostCount() + 1);
                userService.updateById(user);
            }
        } else if (wasPublished && !isPublished) {
            // 发布 -> 草稿：减少原分类和用户计数
            if (originalCategoryId != null) {
                Category category = categoryService.getById(originalCategoryId);
                if (category != null && category.getPostCount() != null && category.getPostCount() > 0) {
                    category.setPostCount(category.getPostCount() - 1);
                    categoryService.updateById(category);
                }
            }
            User user = userService.getById(existingPost.getUserId());
            if (user != null && user.getPostCount() != null && user.getPostCount() > 0) {
                user.setPostCount(user.getPostCount() - 1);
                userService.updateById(user);
            }
        }
        
        // 更新图片（如果提供了新的图片列表）
        List<String> images = updateDTO.getImages();
        if (images == null) {
            images = updateDTO.getImageUrls();
        }
        if (images != null) {
            // 删除旧图片
            LambdaQueryWrapper<PostImage> imageWrapper = new LambdaQueryWrapper<>();
            imageWrapper.eq(PostImage::getPostId, id);
            postImageService.remove(imageWrapper);
            
            // 添加新图片
            for (String imageUrl : images) {
                PostImage postImage = new PostImage();
                postImage.setPostId(id);
                postImage.setImageUrl(imageUrl);
                postImageService.save(postImage);
            }
        }
        
        // 更新视频
        if (updateDTO.getVideo() != null || updateDTO.getVideoUrl() != null) {
            // 删除旧视频
            LambdaQueryWrapper<PostVideo> videoWrapper = new LambdaQueryWrapper<>();
            videoWrapper.eq(PostVideo::getPostId, id);
            postVideoService.remove(videoWrapper);
            
            // 添加新视频
            PostVideo postVideo = new PostVideo();
            postVideo.setPostId(id);
            if (updateDTO.getVideo() != null) {
                postVideo.setVideoUrl(updateDTO.getVideo().getUrl());
                postVideo.setCoverUrl(updateDTO.getVideo().getCoverUrl());
            } else {
                postVideo.setVideoUrl(updateDTO.getVideoUrl());
                postVideo.setCoverUrl(updateDTO.getCoverUrl());
            }
            postVideoService.save(postVideo);
        }
        
        // 更新标签（如果提供了新的标签列表）
        if (updateDTO.getTags() != null) {
            // 获取旧标签
            LambdaQueryWrapper<PostTag> tagWrapper = new LambdaQueryWrapper<>();
            tagWrapper.eq(PostTag::getPostId, id);
            List<PostTag> oldTags = postTagService.list(tagWrapper);
            
            // 减少旧标签使用计数
            for (PostTag oldTag : oldTags) {
                tagService.decrementUseCount(oldTag.getTagId());
            }
            
            // 删除旧标签关联
            postTagService.remove(tagWrapper);
            
            // 添加新标签
            for (Object tagObj : updateDTO.getTags()) {
                Integer tagId = null;
                if (tagObj instanceof Integer) {
                    tagId = (Integer) tagObj;
                } else if (tagObj instanceof Number) {
                    tagId = ((Number) tagObj).intValue();
                } else if (tagObj instanceof String) {
                    try {
                        tagId = Integer.parseInt((String) tagObj);
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (tagId != null) {
                    PostTag postTag = new PostTag();
                    postTag.setPostId(id);
                    postTag.setTagId(tagId);
                    postTagService.save(postTag);
                    // 增加标签使用计数
                    tagService.incrementUseCount(tagId);
                }
            }
        }
        
        return AdminResult.success("笔记更新成功");
    }

    /**
     * 删除笔记
     */
    @DeleteMapping("/{id}")
    public AdminResult<Void> delete(@PathVariable Long id) {
        Post post = postService.getById(id);
        if (post == null) {
            return AdminResult.notFound("笔记不存在");
        }
        
        // 删除关联的图片
        LambdaQueryWrapper<PostImage> imageWrapper = new LambdaQueryWrapper<>();
        imageWrapper.eq(PostImage::getPostId, id);
        postImageService.remove(imageWrapper);
        
        // 删除关联的视频
        LambdaQueryWrapper<PostVideo> videoWrapper = new LambdaQueryWrapper<>();
        videoWrapper.eq(PostVideo::getPostId, id);
        postVideoService.remove(videoWrapper);
        
        // 删除关联的标签并减少使用计数
        LambdaQueryWrapper<PostTag> tagWrapper = new LambdaQueryWrapper<>();
        tagWrapper.eq(PostTag::getPostId, id);
        List<PostTag> postTags = postTagService.list(tagWrapper);
        for (PostTag postTag : postTags) {
            tagService.decrementUseCount(postTag.getTagId());
        }
        postTagService.remove(tagWrapper);
        
        // 删除关联的点赞
        LambdaQueryWrapper<Like> likeWrapper = new LambdaQueryWrapper<>();
        likeWrapper.eq(Like::getTargetId, id).eq(Like::getTargetType, "post");
        likeService.remove(likeWrapper);
        
        // 删除关联的评论
        LambdaQueryWrapper<Comment> commentWrapper = new LambdaQueryWrapper<>();
        commentWrapper.eq(Comment::getPostId, id);
        commentService.remove(commentWrapper);
        
        // 删除关联的收藏
        LambdaQueryWrapper<com.xiaolvshu.entity.Collection> collectionWrapper = new LambdaQueryWrapper<>();
        collectionWrapper.eq(com.xiaolvshu.entity.Collection::getPostId, id);
        collectionService.remove(collectionWrapper);
        
        // 删除笔记
        postService.removeById(id);
        
        // 更新分类的笔记数（仅非草稿时）
        if (post.getIsDraft() == null || post.getIsDraft() == 0) {
            if (post.getCategoryId() != null) {
                Category category = categoryService.getById(post.getCategoryId());
                if (category != null && category.getPostCount() != null && category.getPostCount() > 0) {
                    category.setPostCount(category.getPostCount() - 1);
                    categoryService.updateById(category);
                }
            }
            // 更新用户的笔记数
            User user = userService.getById(post.getUserId());
            if (user != null && user.getPostCount() != null && user.getPostCount() > 0) {
                user.setPostCount(user.getPostCount() - 1);
                userService.updateById(user);
            }
        }
        
        return AdminResult.success("笔记删除成功");
    }

    /**
     * 批量删除笔记
     */
    @DeleteMapping("/batch")
    public AdminResult<Map<String, Object>> batchDelete(@RequestBody BatchDeleteDTO deleteDTO) {
        if (deleteDTO.getIds() == null || deleteDTO.getIds().isEmpty()) {
            return AdminResult.badRequest("请选择要删除的笔记");
        }
        
        int successCount = 0;
        List<Long> failedIds = new ArrayList<>();
        
        for (Long id : deleteDTO.getIds()) {
            try {
                Post post = postService.getById(id);
                if (post != null) {
                    // 删除关联数据
                    LambdaQueryWrapper<PostImage> imageWrapper = new LambdaQueryWrapper<>();
                    imageWrapper.eq(PostImage::getPostId, id);
                    postImageService.remove(imageWrapper);
                    
                    LambdaQueryWrapper<PostVideo> videoWrapper = new LambdaQueryWrapper<>();
                    videoWrapper.eq(PostVideo::getPostId, id);
                    postVideoService.remove(videoWrapper);
                    
                    LambdaQueryWrapper<PostTag> tagWrapper = new LambdaQueryWrapper<>();
                    tagWrapper.eq(PostTag::getPostId, id);
                    List<PostTag> postTags = postTagService.list(tagWrapper);
                    for (PostTag postTag : postTags) {
                        tagService.decrementUseCount(postTag.getTagId());
                    }
                    postTagService.remove(tagWrapper);
                    
                    LambdaQueryWrapper<Like> likeWrapper = new LambdaQueryWrapper<>();
                    likeWrapper.eq(Like::getTargetId, id).eq(Like::getTargetType, "post");
                    likeService.remove(likeWrapper);
                    
                    LambdaQueryWrapper<Comment> commentWrapper = new LambdaQueryWrapper<>();
                    commentWrapper.eq(Comment::getPostId, id);
                    commentService.remove(commentWrapper);
                    
                    LambdaQueryWrapper<com.xiaolvshu.entity.Collection> collectionWrapper = new LambdaQueryWrapper<>();
                    collectionWrapper.eq(com.xiaolvshu.entity.Collection::getPostId, id);
                    collectionService.remove(collectionWrapper);
                    
                    postService.removeById(id);
                    
                    // 更新分类的笔记数（仅非草稿时）
                    if (post.getIsDraft() == null || post.getIsDraft() == 0) {
                        if (post.getCategoryId() != null) {
                            Category category = categoryService.getById(post.getCategoryId());
                            if (category != null && category.getPostCount() != null && category.getPostCount() > 0) {
                                category.setPostCount(category.getPostCount() - 1);
                                categoryService.updateById(category);
                            }
                        }
                        // 更新用户的笔记数
                        User user = userService.getById(post.getUserId());
                        if (user != null && user.getPostCount() != null && user.getPostCount() > 0) {
                            user.setPostCount(user.getPostCount() - 1);
                            userService.updateById(user);
                        }
                    }
                    
                    successCount++;
                }
            } catch (Exception e) {
                failedIds.add(id);
            }
        }
        
        return AdminResult.success("批量删除完成", Map.of("successCount", successCount, "failedIds", failedIds));
    }

    /**
     * 更新笔记状态（发布/草稿）
     */
    @PutMapping("/{id}/status")
    public AdminResult<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer isDraft = body.get("isDraft");
        if (isDraft == null) {
            return AdminResult.badRequest("缺少参数: isDraft");
        }
        
        Post post = postService.getById(id);
        if (post == null) {
            return AdminResult.notFound("笔记不存在");
        }
        
        Integer originalIsDraft = post.getIsDraft();
        
        post.setIsDraft(isDraft);
        post.setUpdatedAt(LocalDateTime.now());
        postService.updateById(post);
        
        // 处理状态切换时的计数变化
        boolean wasPublished = originalIsDraft == null || originalIsDraft == 0;
        boolean isPublished = isDraft == 0;
        
        if (!wasPublished && isPublished) {
            // 草稿 -> 发布：增加计数
            if (post.getCategoryId() != null) {
                Category category = categoryService.getById(post.getCategoryId());
                if (category != null) {
                    category.setPostCount(category.getPostCount() == null ? 1L : category.getPostCount() + 1);
                    categoryService.updateById(category);
                }
            }
            User user = userService.getById(post.getUserId());
            if (user != null) {
                user.setPostCount(user.getPostCount() == null ? 1 : user.getPostCount() + 1);
                userService.updateById(user);
            }
        } else if (wasPublished && !isPublished) {
            // 发布 -> 草稿：减少计数
            if (post.getCategoryId() != null) {
                Category category = categoryService.getById(post.getCategoryId());
                if (category != null && category.getPostCount() != null && category.getPostCount() > 0) {
                    category.setPostCount(category.getPostCount() - 1);
                    categoryService.updateById(category);
                }
            }
            User user = userService.getById(post.getUserId());
            if (user != null && user.getPostCount() != null && user.getPostCount() > 0) {
                user.setPostCount(user.getPostCount() - 1);
                userService.updateById(user);
            }
        }
        
        return AdminResult.success("状态更新成功");
    }

    /**
     * 转换为DTO
     */
    private AdminPostDTO convertToDTO(Post post) {
        AdminPostDTO dto = new AdminPostDTO();
        dto.setId(post.getId());
        dto.setUserId(post.getUserId());
        dto.setTitle(post.getTitle());
        dto.setContent(post.getContent());
        dto.setType(post.getType());
        dto.setCategoryId(post.getCategoryId());
        dto.setViewCount(post.getViewCount());
        dto.setLikeCount(post.getLikeCount());
        dto.setCollectCount(post.getCollectCount());
        dto.setCommentCount(post.getCommentCount());
        dto.setIsDraft(post.getIsDraft());
        dto.setCreatedAt(post.getCreatedAt());
        
        // 获取作者信息
        if (post.getUserId() != null) {
            User user = userService.getById(post.getUserId());
            if (user != null) {
                dto.setNickname(user.getNickname());
                dto.setUserDisplayId(user.getUserId());
            }
        }
        
        // 获取分类信息
        if (post.getCategoryId() != null) {
            Category category = categoryService.getById(post.getCategoryId());
            if (category != null) {
                dto.setCategory(category.getName());
            }
        }
        
        // 获取图片列表
        LambdaQueryWrapper<PostImage> imageWrapper = new LambdaQueryWrapper<>();
        imageWrapper.eq(PostImage::getPostId, post.getId());
        List<PostImage> images = postImageService.list(imageWrapper);
        dto.setImages(images.stream().map(PostImage::getImageUrl).collect(Collectors.toList()));
        
        // 获取视频信息
        LambdaQueryWrapper<PostVideo> videoWrapper = new LambdaQueryWrapper<>();
        videoWrapper.eq(PostVideo::getPostId, post.getId());
        PostVideo video = postVideoService.getOne(videoWrapper);
        if (video != null) {
            dto.setVideoUrl(video.getVideoUrl());
            dto.setCoverUrl(video.getCoverUrl());
        }
        
        // 获取标签列表
        LambdaQueryWrapper<PostTag> tagWrapper = new LambdaQueryWrapper<>();
        tagWrapper.eq(PostTag::getPostId, post.getId());
        List<PostTag> postTags = postTagService.list(tagWrapper);
        if (!postTags.isEmpty()) {
            List<Integer> tagIds = postTags.stream().map(PostTag::getTagId).collect(Collectors.toList());
            List<Tag> tags = tagService.listByIds(tagIds);
            List<AdminPostDTO.AdminTagDTO> tagDTOs = tags.stream().map(tag -> {
                AdminPostDTO.AdminTagDTO tagDTO = new AdminPostDTO.AdminTagDTO();
                tagDTO.setId(tag.getId());
                tagDTO.setName(tag.getName());
                return tagDTO;
            }).collect(Collectors.toList());
            dto.setTags(tagDTOs);
        } else {
            dto.setTags(new ArrayList<>());
        }
        
        return dto;
    }
}
