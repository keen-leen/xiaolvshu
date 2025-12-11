package com.xiaolvshu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaolvshu.context.UserContext;
import com.xiaolvshu.dto.*;
import com.xiaolvshu.entity.*;
import com.xiaolvshu.exception.BusinessException;
import com.xiaolvshu.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 帖子服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {
    
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;
    private final PostImageMapper postImageMapper;
    private final PostVideoMapper postVideoMapper;
    private final PostTagMapper postTagMapper;
    private final TagMapper tagMapper;
    private final LikeMapper likeMapper;
    private final CollectionMapper collectionMapper;

    /**
     * 创建帖子
     */
    @Transactional
    public PostResponse createPost(Long userId, CreatePostRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        
        Post post = new Post();
        post.setUserId(userId);
        post.setContent(request.getContent());
        
        postMapper.insert(post);
        
        return convertToResponse(post, null, null, null, null, null);
    }
    
    /**
     * 获取帖子列表
     */
    public PageResult<PostResponse> getPosts(PostRequest request) {
        Integer page = request.getPage();
        Integer limit = request.getLimit();
        String category = request.getCategory();
        Integer type = request.getType();
        Integer isDraft = request.getIsDraft();
        Long userId = request.getUserId();
        Page<Post> pageParam = new Page<>(page, limit);

        // 暂时将推荐置为全部
        if (category != null && category.equals("recommend")) {
            category = null;
        }
        
        IPage<Post> result = postMapper.selectPage(pageParam, 
            new LambdaQueryWrapper<Post>()
                .eq(category != null, Post::getCategoryId, category)
                .eq(isDraft != null, Post::getIsDraft, isDraft)
                .eq(userId != null, Post::getUserId, userId)
                .eq(type != null, Post::getType, type)
                .orderByDesc(Post::getCreatedAt));
        
        Long total = result.getTotal();
        log.info("获取帖子列表，页码：{}，每页数量：{}，分类：{}，类型：{}，用户ID：{}，总数：{}", page, limit, category, type, userId, total);
        
        List<Post> posts = result.getRecords();
        if (posts.isEmpty()) {
            return PageResult.empty(page, limit);
        }
        
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        
        // 查找帖子图片
        Map<Long, List<String>> imageMap = postImageMapper.selectList(
            new LambdaQueryWrapper<PostImage>()
                .in(PostImage::getPostId, postIds))
                .stream()
                .collect(Collectors.groupingBy(
                    PostImage::getPostId,
                    Collectors.mapping(PostImage::getImageUrl, Collectors.toList())
                ));
        
        // 查找帖子视频
        Map<Long, PostVideo> videoMap = postVideoMapper.selectList(
            new LambdaQueryWrapper<PostVideo>()
                .in(PostVideo::getPostId, postIds))
                .stream()
                .collect(Collectors.toMap(PostVideo::getPostId, v -> v, (v1, v2) -> v1));
        
        // 查找帖子标签
        List<PostTag> postTags = postTagMapper.selectList(
            new LambdaQueryWrapper<PostTag>()
                .in(PostTag::getPostId, postIds));
        List<Integer> tagIds = postTags.stream().map(PostTag::getTagId).distinct().toList();
        Map<Integer, Tag> tagMap = tagIds.isEmpty() ? Collections.emptyMap() :
            tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, t -> t));
        Map<Long, List<TagDTO>> postTagMap = postTags.stream()
            .collect(Collectors.groupingBy(
                PostTag::getPostId,
                Collectors.mapping(pt -> {
                    Tag tag = tagMap.get(pt.getTagId());
                    if (tag != null) {
                        TagDTO tagDTO = new TagDTO();
                        tagDTO.setId(tag.getId());
                        tagDTO.setName(tag.getName());
                        return tagDTO;
                    }
                    return null;
                }, Collectors.filtering(t -> t != null, Collectors.toList()))
            ));
        
        // 查找用户信息
        List<Long> userIds = posts.stream().map(Post::getUserId).distinct().toList();
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        
        // 查找分类信息
        List<Integer> categoryIds = posts.stream()
            .map(Post::getCategoryId)
            .filter(id -> id != null)
            .distinct()
            .toList();
        Map<Integer, Category> categoryMap = categoryIds.isEmpty() ? Collections.emptyMap() :
            categoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, c -> c));
        
        // 查找当前用户的点赞和收藏状态
        final Set<Long> likedSet;
        final Set<Long> collectedSet;
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null) {
            likedSet = likeMapper.selectList(
                new LambdaQueryWrapper<Like>()
                    .eq(Like::getUserId, currentUserId)
                    .eq(Like::getTargetType, 1)
                    .in(Like::getTargetId, postIds))
                .stream()
                .map(Like::getTargetId)
                .collect(Collectors.toSet());
            
            collectedSet = collectionMapper.selectList(
                new LambdaQueryWrapper<Collection>()
                    .eq(Collection::getUserId, currentUserId)
                    .in(Collection::getPostId, postIds))
                .stream()
                .map(Collection::getPostId)
                .collect(Collectors.toSet());
        } else {
            likedSet = Collections.emptySet();
            collectedSet = Collections.emptySet();
        }
        
        // 转换为响应对象
        List<PostResponse> postResponses = posts.stream()
            .map(post -> convertToResponse(
                post,
                userMap.get(post.getUserId()),
                categoryMap.get(post.getCategoryId()),
                imageMap.get(post.getId()),
                videoMap.get(post.getId()),
                postTagMap.get(post.getId()),
                likedSet.contains(post.getId()),
                collectedSet.contains(post.getId())
            ))
            .toList();
        
        return new PageResult<>(postResponses, page, limit, total);
    }
    
    /**
     * 获取帖子详情
     */
    @Transactional
    public PostResponse getPostById(Long id) {
        Post post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException("帖子不存在");
        }
        
        // 增加浏览量
        post.setViewCount(post.getViewCount() + 1);
        postMapper.updateById(post);
        
        // 获取图片
        List<String> images = postImageMapper.selectList(
            new LambdaQueryWrapper<PostImage>()
                .eq(PostImage::getPostId, id))
            .stream()
            .map(PostImage::getImageUrl)
            .toList();
        
        // 获取视频
        PostVideo video = postVideoMapper.selectOne(
            new LambdaQueryWrapper<PostVideo>()
                .eq(PostVideo::getPostId, id)
                .last("LIMIT 1"));
        
        // 获取标签
        List<PostTag> postTags = postTagMapper.selectList(
            new LambdaQueryWrapper<PostTag>()
                .eq(PostTag::getPostId, id));
        List<TagDTO> tags = new ArrayList<>();
        if (!postTags.isEmpty()) {
            List<Integer> tagIds = postTags.stream().map(PostTag::getTagId).toList();
            tags = tagMapper.selectBatchIds(tagIds).stream()
                .map(tag -> {
                    TagDTO tagDTO = new TagDTO();
                    tagDTO.setId(tag.getId());
                    tagDTO.setName(tag.getName());
                    return tagDTO;
                })
                .toList();
        }
        
        // 获取用户信息
        User user = userMapper.selectById(post.getUserId());
        
        // 获取分类信息
        Category category = post.getCategoryId() != null ? 
            categoryMapper.selectById(post.getCategoryId()) : null;
        
        // 检查点赞和收藏状态
        boolean liked = false;
        boolean collected = false;
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null) {
            liked = likeMapper.selectCount(
                new LambdaQueryWrapper<Like>()
                    .eq(Like::getUserId, currentUserId)
                    .eq(Like::getTargetType, 1)
                    .eq(Like::getTargetId, id)) > 0;
            
            collected = collectionMapper.selectCount(
                new LambdaQueryWrapper<Collection>()
                    .eq(Collection::getUserId, currentUserId)
                    .eq(Collection::getPostId, id)) > 0;
        }
        
        return convertToResponse(post, user, category, images, video, tags, liked, collected);
    }
    
    /**
     * 删除帖子
     */
    @Transactional
    public void deletePost(Long userId, Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("帖子不存在");
        }
        
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException("无权删除此帖子");
        }
        
        postMapper.deleteById(postId);
    }
    
    /**
     * 搜索帖子
     */
    public PageResult<PostResponse> searchPosts(String keyword, int page, int limit, Long currentUserId) {
        Page<Post> pageParam = new Page<>(page, limit);
        IPage<Post> result = postMapper.selectPage(pageParam,
            new LambdaQueryWrapper<Post>()
                .eq(Post::getIsDraft, 0)
                .and(wrapper -> wrapper
                    .like(Post::getTitle, keyword)
                    .or()
                    .like(Post::getContent, keyword))
                .orderByDesc(Post::getCreatedAt));
        
        // 构建响应（简化处理，实际可以提取公共方法）
        List<Post> posts = result.getRecords();
        if (posts.isEmpty()) {
            return PageResult.empty(page, limit);
        }
        
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        
        // 查找帖子图片
        Map<Long, List<String>> imageMap = postImageMapper.selectList(
            new LambdaQueryWrapper<PostImage>()
                .in(PostImage::getPostId, postIds))
            .stream()
            .collect(Collectors.groupingBy(
                PostImage::getPostId,
                Collectors.mapping(PostImage::getImageUrl, Collectors.toList())
            ));
        
        // 查找用户信息
        List<Long> userIds = posts.stream().map(Post::getUserId).distinct().toList();
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds)
            .stream()
            .collect(Collectors.toMap(User::getId, user -> user));
        
        List<PostResponse> postResponses = posts.stream()
            .map(post -> convertToResponse(
                post,
                userMap.get(post.getUserId()),
                null,
                imageMap.get(post.getId()),
                null,
                null,
                false,
                false
            ))
            .toList();
        
        return new PageResult<>(postResponses, page, limit, result.getTotal());
    }
    
    /**
     * 转换为响应对象（简化版）
     */
    private PostResponse convertToResponse(Post post, User user, Category category,
                                           List<String> images, PostVideo video, List<TagDTO> tags) {
        return convertToResponse(post, user, category, images, video, tags, false, false);
    }
    
    /**
     * 转换为响应对象
     */
    private PostResponse convertToResponse(Post post, User user, Category category,
                                           List<String> images, PostVideo video, List<TagDTO> tags,
                                           boolean liked, boolean collected) {
        PostResponse response = new PostResponse();
        
        // 帖子基本信息
        response.setId(post.getId());
        response.setTitle(post.getTitle());
        response.setContent(post.getContent());
        response.setCategoryId(post.getCategoryId());
        response.setType(post.getType());
        response.setIsDraft(post.getIsDraft());
        response.setCreatedAt(post.getCreatedAt());
        
        // 统计数据
        response.setViewCount(post.getViewCount());
        response.setLikeCount(post.getLikeCount());
        response.setCollectCount(post.getCollectCount());
        response.setCommentCount(post.getCommentCount());
        
        // 分类信息
        if (category != null) {
            response.setCategory(category.getName());
        }
        
        // 媒体资源
        if (post.getType() != null && post.getType() == 2) {
            // 视频笔记
            if (video != null) {
                response.setVideoUrl(video.getVideoUrl());
                response.setCoverUrl(video.getCoverUrl());
                response.setImage(video.getCoverUrl());
                if (video.getCoverUrl() != null) {
                    response.setImages(List.of(video.getCoverUrl()));
                }
            }
        } else {
            // 图文笔记
            if (images != null && !images.isEmpty()) {
                response.setImages(images);
                response.setImage(images.get(0));
            }
        }
        
        // 标签
        response.setTags(tags != null ? tags : Collections.emptyList());
        
        // 作者信息
        response.setUserId(post.getUserId());
        if (user != null) {
            response.setAuthorAutoId(user.getId());
            response.setAuthorAccount(user.getUserId());
            response.setNickname(user.getNickname());
            response.setUserAvatar(user.getAvatar());
            response.setLocation(user.getLocation());
            response.setVerified(user.getVerified());
        }
        
        // 当前用户状态
        response.setLiked(liked);
        response.setCollected(collected);
        
        return response;
    }
}
