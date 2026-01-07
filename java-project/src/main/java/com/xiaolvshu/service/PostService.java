package com.xiaolvshu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaolvshu.context.UserContext;
import com.xiaolvshu.dto.*;
import com.xiaolvshu.dto.CreatePostRequest.Video;
import com.xiaolvshu.entity.*;
import com.xiaolvshu.exception.BusinessException;
import com.xiaolvshu.mapper.*;
import com.xiaolvshu.utils.MentionParser;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class PostService extends ServiceImpl<PostMapper, Post> {

    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;
    private final PostImageMapper postImageMapper;
    private final PostVideoMapper postVideoMapper;
    private final PostTagMapper postTagMapper;
    private final TagMapper tagMapper;
    private final LikeMapper likeMapper;
    private final CollectionMapper collectionMapper;
    private final NotificationMapper notificationMapper;

    public PostService(PostMapper postMapper, UserMapper userMapper, CategoryMapper categoryMapper,
                       PostImageMapper postImageMapper, PostVideoMapper postVideoMapper,
                       PostTagMapper postTagMapper, TagMapper tagMapper,
                       LikeMapper likeMapper, CollectionMapper collectionMapper,
                       NotificationMapper notificationMapper) {
        this.userMapper = userMapper;
        this.categoryMapper = categoryMapper;
        this.postImageMapper = postImageMapper;
        this.postVideoMapper = postVideoMapper;
        this.postTagMapper = postTagMapper;
        this.tagMapper = tagMapper;
        this.likeMapper = likeMapper;
        this.collectionMapper = collectionMapper;
        this.notificationMapper = notificationMapper;
    }

    /**
     * 创建笔记
     */
    @Transactional
    public PostResponse createPost(CreatePostRequest request) {
        Long userId = UserContext.getUserId();
        User user = userMapper.selectById(userId);
        String title = request.getTitle();
        String content = request.getContent();
        Integer categoryId = request.getCategoryId();
        Integer isDraft = request.isDraft() ? 1 : 0;
        if (isDraft == 0) {
            if (title == null || title.isBlank() || content == null || content.isBlank()) {
                throw new BusinessException("发布时标题和内容不能为空");
            }
        }
        Integer postType = request.getType();
        if (postType != 1 && postType != 2) {
            throw new BusinessException("无效的发布类型");
        }
        // 创建笔记
        Post post = new Post();
        post.setUserId(userId);
        post.setTitle(title);
        post.setContent(content);
        post.setCategoryId(categoryId);
        post.setIsDraft(isDraft);
        post.setType(postType);
        baseMapper.insert(post);

        Long postId = post.getId();
        if (postId == null) {
            throw new BusinessException("帖子创建失败");
        }

        List<String> imageUrls = new ArrayList<>();
        PostVideo postVideo = null;

        // 处理图片（图文类型）
        if (postType == 1 && request.getImages() != null && request.getImages().length > 0) {
            for (String imageUrl : request.getImages()) {
                if (imageUrl == null || imageUrl.isBlank()) {
                    continue;
                }
                String url = imageUrl.trim();
                if (url.isEmpty()) {
                    continue;
                }
                PostImage postImage = new PostImage();
                postImage.setPostId(postId);
                postImage.setImageUrl(url);
                postImageMapper.insert(postImage);
                imageUrls.add(url);
            }
        }
        // 处理视频（视频类型）
        else if (postType == 2 && request.getVideo() != null && request.getVideo().getUrl() != null
                && !request.getVideo().getUrl().isBlank()) {
            postVideo = new PostVideo();
            postVideo.setPostId(postId);
            postVideo.setVideoUrl(request.getVideo().getUrl().trim());
            if (request.getVideo().getCoverUrl() != null && !request.getVideo().getCoverUrl().isBlank()) {
                postVideo.setCoverUrl(request.getVideo().getCoverUrl().trim());
            }
            postVideoMapper.insert(postVideo);
        }

        // 处理标签
        List<TagDTO> tags = new ArrayList<>();
        if (request.getTags() != null) {
            for (String rawTagName : request.getTags()) {
                if (rawTagName == null || rawTagName.isBlank()) {
                    continue;
                }
                String tagName = rawTagName.trim();
                if (tagName.isEmpty()) {
                    continue;
                }

                Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName, tagName));
                if (tag == null) {
                    tag = new Tag();
                    tag.setName(tagName);
                    tag.setUseCount(0);
                    tagMapper.insert(tag);
                }

                PostTag postTag = new PostTag();
                postTag.setPostId(postId);
                postTag.setTagId(tag.getId());
                postTagMapper.insert(postTag);
                // 增加标签使用数
                tag.setUseCount(tag.getUseCount() + 1);
                tagMapper.updateById(tag);

                TagDTO dto = new TagDTO();
                dto.setId(tag.getId());
                dto.setName(tag.getName());
                tags.add(dto);
            }
        }

        // 处理@用户通知（仅在发布笔记时）
        if ((isDraft == null || isDraft == 0) && MentionParser.hasMentions(post.getContent())) {
            List<MentionParser.MentionedUser> mentionedUsers = MentionParser.extractMentionedUsers(post.getContent());
            for (MentionParser.MentionedUser mentionedUser : mentionedUsers) {
                if (mentionedUser == null || mentionedUser.getUserId() == null || mentionedUser.getUserId().isBlank()) {
                    continue;
                }
                User mentioned = userMapper
                        .selectOne(new LambdaQueryWrapper<User>().eq(User::getUserId, mentionedUser.getUserId()));
                if (mentioned == null) {
                    continue;
                }
                if (mentioned.getId().equals(userId)) {
                    continue;
                }
                Notification notification = new Notification();
                notification.setUserId(mentioned.getId());
                notification.setSenderId(userId);
                notification.setType(Notification.TYPE_MENTION_POST);
                notification.setTargetId(post.getId());
                notification.setIsRead(0);
                notification.setTitle("在笔记中@了你");
                notificationMapper.insert(notification);
            }
        }

        Category category = null;
        if (categoryId != null) {
            category = categoryMapper.selectById(categoryId);
        }
        // 发布时更新分类下的笔记数和用户的笔记数
        if (isDraft == null || isDraft == 0) {
            // 更新分类的帖子数
            if (category != null) {
                category.setPostCount(category.getPostCount() == null ? 1 : category.getPostCount() + 1);
                categoryMapper.updateById(category);
            }
            // 更新用户的帖子数
            user.setPostCount(user.getPostCount() == null ? 1 : user.getPostCount() + 1);
            userMapper.updateById(user);
        }
        log.info("发布笔记成功, 笔记ID:{},用户ID:{}", post.getId(), userId);
        return convertToResponse(post, user, category, imageUrls, postVideo, tags);
    }

    public PostResponse updatePost(Long postId, CreatePostRequest request) {
        Post post = baseMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("笔记不存在");
        }
        Long userId = UserContext.getUserId();
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException("无权更新此笔记");
        }

        // 原始内容（用于 @ 通知增量处理）
        Integer originalIsDraft = post.getIsDraft();
        String originalContent = post.getContent() == null ? "" : post.getContent();

        boolean isDraft = request.isDraft();
        // 验证必填字段：发布状态下要求标题、内容、分类
        if (!isDraft && (request.getTitle() == null || request.getTitle().isBlank() || request.getContent() == null
                || request.getContent().isBlank() || request.getCategoryId() == null)) {
            throw new BusinessException("发布时标题、内容和分类不能为空");
        }

        // 更新基本信息（不更改 type，沿用原有类型）
        post.setTitle(request.getTitle() == null ? "" : request.getTitle());
        post.setContent(request.getContent() == null ? "" : request.getContent());
        post.setCategoryId(request.getCategoryId());
        post.setIsDraft(isDraft ? 1 : 0);
        baseMapper.updateById(post);

        postId = post.getId();
        Integer postType = post.getType();

        // 媒体更新
        if (Objects.equals(postType, 2)) {
            // 视频笔记处理
            PostVideo oldVideo = postVideoMapper
                    .selectOne(new LambdaQueryWrapper<PostVideo>().eq(PostVideo::getPostId, postId).last("LIMIT 1"));

            Video newVideo = request.getVideo();
            if (newVideo != null && (newVideo.getUrl() != null || newVideo.getCoverUrl() != null)) {
                String newVideoUrl = null;
                String newCoverUrl = null;
                if (newVideo.getUrl() != null && !newVideo.getUrl().isBlank()) {
                    newVideoUrl = newVideo.getUrl();
                    newCoverUrl = newVideo.getCoverUrl();
                } else if (newVideo.getCoverUrl() != null && oldVideo != null) {
                    // 仅更新封面
                    newVideoUrl = oldVideo.getVideoUrl();
                    newCoverUrl = newVideo.getCoverUrl();
                }
                if (newVideoUrl != null) {
                    // 删除旧记录后重建
                    if (oldVideo != null) {
                        postVideoMapper.deleteById(oldVideo.getId());
                    }
                    PostVideo postVideo = new PostVideo();
                    postVideo.setPostId(postId);
                    postVideo.setVideoUrl(newVideoUrl);
                    if (newCoverUrl != null && !newCoverUrl.isBlank()) {
                        postVideo.setCoverUrl(newCoverUrl);
                    }
                    postVideoMapper.insert(postVideo);
                    // 文件清理留给异步/外部策略，这里仅更新数据库
                }
            }
        } else {
            // 图文笔记：重建图片列表
            postImageMapper.delete(new LambdaQueryWrapper<PostImage>().eq(PostImage::getPostId, postId));
            if (request.getImages() != null && request.getImages().length > 0) {
                for (String raw : request.getImages()) {
                    if (raw == null || raw.isBlank()) {
                        continue;
                    }
                    PostImage img = new PostImage();
                    img.setPostId(postId);
                    img.setImageUrl(raw);
                    postImageMapper.insert(img);
                }
            }
        }

        // 标签处理：先记录旧标签
        List<PostTag> oldPostTags = postTagMapper
                .selectList(new LambdaQueryWrapper<PostTag>().eq(PostTag::getPostId, postId));
        Map<Integer, Tag> oldTagMap = Collections.emptyMap();
        if (!oldPostTags.isEmpty()) {
            List<Integer> oldTagIds = oldPostTags.stream().map(PostTag::getTagId).distinct().toList();
            oldTagMap = tagMapper.selectBatchIds(oldTagIds).stream().collect(Collectors.toMap(Tag::getId, t -> t));
        }
        Set<String> oldTagNames = oldTagMap.values().stream().map(Tag::getName).collect(Collectors.toSet());

        // 新标签集合
        Set<String> newTagNames = new HashSet<>();
        if (request.getTags() != null) {
            for (String raw : request.getTags()) {
                if (raw != null && !raw.isBlank()) {
                    newTagNames.add(raw.trim());
                }
            }
        }

        // 计算差集
        Set<String> tagsToRemove = new HashSet<>(oldTagNames);
        tagsToRemove.removeAll(newTagNames);

        Set<String> tagsToAdd = new HashSet<>(newTagNames);
        tagsToAdd.removeAll(oldTagNames);

        // 删除旧关联
        postTagMapper.delete(new LambdaQueryWrapper<PostTag>().eq(PostTag::getPostId, postId));

        // 减少已移除标签的使用次数
        for (String tagName : tagsToRemove) {
            Tag tag = oldTagMap.values().stream().filter(t -> tagName.equals(t.getName())).findFirst().orElse(null);
            if (tag != null && tag.getUseCount() != null) {
                tag.setUseCount(Math.max(0, tag.getUseCount() - 1));
                tagMapper.updateById(tag);
            }
        }

        // 重新插入新标签关联，并对新增标签增加使用次数
        List<TagDTO> responseTags = new ArrayList<>();
        if (!newTagNames.isEmpty()) {
            for (String tagName : newTagNames) {
                Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName, tagName));
                boolean isNewCreated = false;
                if (tag == null) {
                    tag = new Tag();
                    tag.setName(tagName);
                    tag.setUseCount(0);
                    tagMapper.insert(tag);
                    isNewCreated = true;
                }

                PostTag pt = new PostTag();
                pt.setPostId(postId);
                pt.setTagId(tag.getId());
                postTagMapper.insert(pt);

                if (isNewCreated || tagsToAdd.contains(tagName)) {
                    tag.setUseCount(tag.getUseCount() + 1);
                    tagMapper.updateById(tag);
                }

                TagDTO dto = new TagDTO();
                dto.setId(tag.getId());
                dto.setName(tag.getName());
                responseTags.add(dto);
            }
        }

        // @ 通知处理（仅非草稿）
        if (!isDraft) {
            Set<String> newMentionedUserIds = MentionParser.hasMentions(post.getContent())
                    ? MentionParser.extractMentionedUsers(post.getContent()).stream()
                            .map(MentionParser.MentionedUser::getUserId).filter(id -> id != null && !id.isBlank())
                            .collect(Collectors.toSet())
                    : Collections.emptySet();

            Set<String> oldMentionedUserIds = Collections.emptySet();
            if (originalIsDraft != null && originalIsDraft == 0 && MentionParser.hasMentions(originalContent)) {
                oldMentionedUserIds = MentionParser.extractMentionedUsers(originalContent).stream()
                        .map(MentionParser.MentionedUser::getUserId).filter(id -> id != null && !id.isBlank())
                        .collect(Collectors.toSet());
            }

            Set<String> toRemove = new HashSet<>(oldMentionedUserIds);
            toRemove.removeAll(newMentionedUserIds);
            Set<String> toAdd = new HashSet<>(newMentionedUserIds);
            toAdd.removeAll(oldMentionedUserIds);

            // 删除不再需要且未读的 @ 通知
            for (String mention : toRemove) {
                User mentioned = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUserId, mention));
                if (mentioned != null && !mentioned.getId().equals(userId)) {
                    notificationMapper.delete(new LambdaQueryWrapper<Notification>()
                            .eq(Notification::getUserId, mentioned.getId())
                            .eq(Notification::getSenderId, userId)
                            .eq(Notification::getTargetId, postId)
                            .eq(Notification::getType, Notification.TYPE_MENTION_POST)
                            .eq(Notification::getIsRead, 0));
                }
            }

            // 添加新的 @ 通知
            for (String mention : toAdd) {
                User mentioned = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUserId, mention));
                if (mentioned != null && !mentioned.getId().equals(userId)) {
                    Notification notification = new Notification();
                    notification.setUserId(mentioned.getId());
                    notification.setSenderId(userId);
                    notification.setType(Notification.TYPE_MENTION_POST);
                    notification.setTargetId(postId);
                    notification.setIsRead(0);
                    notification.setTitle("在笔记中@了你");
                    notificationMapper.insert(notification);
                }
            }
        }

        // 准备响应数据
        Category category = post.getCategoryId() != null ? categoryMapper.selectById(post.getCategoryId()) : null;

        List<String> images = postType == 1
                ? postImageMapper.selectList(new LambdaQueryWrapper<PostImage>().eq(PostImage::getPostId, postId))
                        .stream().map(PostImage::getImageUrl).toList()
                : null;
        PostVideo video = postType == 2
                ? postVideoMapper
                        .selectOne(new LambdaQueryWrapper<PostVideo>().eq(PostVideo::getPostId, postId).last("LIMIT 1"))
                : null;

        List<TagDTO> tags = responseTags.isEmpty()
                ? postTagMapper.selectList(new LambdaQueryWrapper<PostTag>().eq(PostTag::getPostId, postId)).stream()
                        .map(pt -> {
                            Tag tag = tagMapper.selectById(pt.getTagId());
                            if (tag == null) {
                                return null;
                            }
                            TagDTO dto = new TagDTO();
                            dto.setId(tag.getId());
                            dto.setName(tag.getName());
                            return dto;
                        }).filter(Objects::nonNull).toList()
                : responseTags;
        User user = userMapper.selectById(userId);

        // 发布时更新分类下的笔记数和用户的笔记数
        if (originalIsDraft != null && originalIsDraft == 1 && !isDraft) {
            // 更新分类的帖子数
            if (category != null) {
                category.setPostCount(category.getPostCount() == null ? 1 : category.getPostCount() + 1);
                categoryMapper.updateById(category);
            }
            // 更新用户的帖子数
            user.setPostCount(user.getPostCount() == null ? 1 : user.getPostCount() + 1);
            userMapper.updateById(user);
        }
        return convertToResponse(post, user, category, images, video, tags, false, false);
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
        String username = request.getUserId();
        Page<Post> pageParam = new Page<>(page, limit);

        User tarUser = null;
        if (username != null) {
            tarUser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUserId, username));
        }
        Long userId = tarUser != null ? tarUser.getId() : null;

        // 暂时将推荐置为全部
        if (category != null && category.equals("recommend")) {
            category = null;
        }

        IPage<Post> result = baseMapper.selectPage(pageParam,
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
                        Collectors.mapping(PostImage::getImageUrl, Collectors.toList())));

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
        Map<Integer, Tag> tagMap = tagIds.isEmpty() ? Collections.emptyMap()
                : tagMapper.selectBatchIds(tagIds).stream()
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
                        }, Collectors.filtering(t -> t != null, Collectors.toList()))));

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
        Map<Integer, Category> categoryMap = categoryIds.isEmpty() ? Collections.emptyMap()
                : categoryMapper.selectBatchIds(categoryIds).stream()
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
                        collectedSet.contains(post.getId())))
                .toList();

        return new PageResult<>(postResponses, page, limit, total);
    }

    /**
     * 获取帖子详情
     */
    @Transactional
    public PostResponse getPostById(Long id, Boolean skipViewCount) {
        Post post = baseMapper.selectById(id);
        if (post == null) {
            throw new BusinessException("帖子不存在");
        }

        // 增加浏览量（支持跳过）
        if (skipViewCount == null || !skipViewCount) {
            post.setViewCount(post.getViewCount() + 1);
            baseMapper.updateById(post);
        }

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
        Category category = post.getCategoryId() != null ? categoryMapper.selectById(post.getCategoryId()) : null;

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
    public void deletePost(Long postId) {
        Post post = baseMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("帖子不存在");
        }
        Long userId = UserContext.getUserId();
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException("无权删除此帖子");
        }
        baseMapper.deleteById(postId);
        // 非草稿时更新分类和用户的帖子数
        if (post.getIsDraft() != null && post.getIsDraft() == 0) {
            // 更新分类的帖子数
            if (post.getCategoryId() != null) {
                Category category = categoryMapper.selectById(post.getCategoryId());
                if (category != null && category.getPostCount() != null && category.getPostCount() > 0) {
                    category.setPostCount(category.getPostCount() - 1);
                    categoryMapper.updateById(category);
                }
            }

            // 更新用户的帖子数
            User user = userMapper.selectById(post.getUserId());
            if (user != null && user.getPostCount() != null && user.getPostCount() > 0) {
                user.setPostCount(user.getPostCount() - 1);
                userMapper.updateById(user);
            }
        }
    }

    /**
     * 搜索帖子
     */
    public PageResult<PostResponse> searchPosts(String keyword, int page, int limit) {
        Page<Post> pageParam = new Page<>(page, limit);
        IPage<Post> result = baseMapper.selectPage(pageParam,
            new LambdaQueryWrapper<Post>()
                .eq(Post::getIsDraft, 0)
                .and(wrapper -> wrapper
                    .like(Post::getTitle, keyword)
                    .or()
                    .like(Post::getContent, keyword))
                .orderByDesc(Post::getCreatedAt));

        List<Post> posts = result.getRecords();
        if (posts.isEmpty()) {
            return PageResult.empty(page, limit);
        }

        List<Long> postIds = posts.stream().map(Post::getId).toList();

        // 图片
        Map<Long, List<String>> imageMap = postImageMapper.selectList(
            new LambdaQueryWrapper<PostImage>()
                .in(PostImage::getPostId, postIds))
            .stream()
            .collect(Collectors.groupingBy(
                PostImage::getPostId,
                Collectors.mapping(PostImage::getImageUrl, Collectors.toList())));

        // 视频封面
        Map<Long, PostVideo> videoMap = postVideoMapper.selectList(
            new LambdaQueryWrapper<PostVideo>()
                .in(PostVideo::getPostId, postIds))
            .stream()
            .collect(Collectors.toMap(PostVideo::getPostId, v -> v, (v1, v2) -> v1));

        // 标签
        List<PostTag> postTags = postTagMapper.selectList(
            new LambdaQueryWrapper<PostTag>()
                .in(PostTag::getPostId, postIds));
        List<Integer> tagIds = postTags.stream().map(PostTag::getTagId).distinct().toList();
        Map<Integer, Tag> tagMap = tagIds.isEmpty() ? Collections.emptyMap()
            : tagMapper.selectBatchIds(tagIds).stream()
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
                }, Collectors.filtering(t -> t != null, Collectors.toList()))));

        // 作者信息
        List<Long> userIds = posts.stream().map(Post::getUserId).distinct().toList();
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds)
            .stream()
            .collect(Collectors.toMap(User::getId, user -> user));

        // 点赞与收藏状态（当前用户）
        final Set<Long> likedSet;
        final Set<Long> collectedSet;
        Long uid = UserContext.getUserId();
        if (uid != null) {
            likedSet = likeMapper.selectList(
                new LambdaQueryWrapper<Like>()
                    .eq(Like::getUserId, uid)
                    .eq(Like::getTargetType, 1)
                    .in(Like::getTargetId, postIds))
                .stream()
                .map(Like::getTargetId)
                .collect(Collectors.toSet());

            collectedSet = collectionMapper.selectList(
                new LambdaQueryWrapper<Collection>()
                    .eq(Collection::getUserId, uid)
                    .in(Collection::getPostId, postIds))
                .stream()
                .map(Collection::getPostId)
                .collect(Collectors.toSet());
        } else {
            likedSet = Collections.emptySet();
            collectedSet = Collections.emptySet();
        }

        List<PostResponse> postResponses = posts.stream()
            .map(post -> convertToResponse(
                post,
                userMap.get(post.getUserId()),
                null,
                imageMap.get(post.getId()),
                videoMap.get(post.getId()),
                postTagMap.get(post.getId()),
                likedSet.contains(post.getId()),
                collectedSet.contains(post.getId())))
            .toList();

        return new PageResult<>(postResponses, page, limit, result.getTotal());
    }

    /**
     * 转换为响应对象（简化版）
     */
    private PostResponse convertToResponse(Post post, User user, Category category, List<String> images,
            PostVideo video, List<TagDTO> tags) {
        return convertToResponse(post, user, category, images, video, tags, false, false);
    }

    /**
     * 转换为响应对象
     */
    private PostResponse convertToResponse(Post post, User user, Category category, List<String> images,
            PostVideo video, List<TagDTO> tags, boolean liked, boolean collected) {
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
                response.setImage(images.getFirst());
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

    /**
     * 搜索接口专用：复用统一的 PostResponse 映射规则
     */
    public PostResponse convertToResponseForSearch(Post post, User user, List<String> images,
            PostVideo video, List<TagDTO> tags, boolean liked, boolean collected) {
        return convertToResponse(post, user, null, images, video, tags, liked, collected);
    }
}
