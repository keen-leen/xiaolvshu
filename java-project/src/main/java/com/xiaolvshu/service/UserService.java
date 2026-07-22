package com.xiaolvshu.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xiaolvshu.common.constant.RedisExpireConstant;
import com.xiaolvshu.context.UserContext;
import com.xiaolvshu.dto.*;
import com.xiaolvshu.entity.Category;
import com.xiaolvshu.entity.Collection;
import com.xiaolvshu.entity.Like;
import com.xiaolvshu.entity.Post;
import com.xiaolvshu.entity.PostImage;
import com.xiaolvshu.entity.PostTag;
import com.xiaolvshu.entity.PostVideo;
import com.xiaolvshu.entity.Tag;
import com.xiaolvshu.entity.User;
import com.xiaolvshu.exception.BusinessException;
import com.xiaolvshu.mapper.*;
import com.xiaolvshu.utils.RedisKeyUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService extends ServiceImpl<UserMapper, User> {
    
    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final LikeMapper likeMapper;
    private final CollectionMapper collectionMapper;
    private final PostImageMapper postImageMapper;
    private final PostVideoMapper postVideoMapper;
    private final PostTagMapper postTagMapper;
    private final TagMapper tagMapper;
    private final CategoryMapper categoryMapper;
    private final AuditMapper auditMapper;
    private final PasswordEncoder passwordEncoder;
    private final CacheService cacheService;

    /**
     * 搜索用户
     */
    public PageResult<UserResponse> searchUsers(String keyword, int page, int limit) {
        Page<User> pageParam = new Page<>(page, limit);
        IPage<User> result = userMapper.selectPage(pageParam,
            new LambdaQueryWrapper<User>()
                .eq(User::getIsActive, 1)
                .and(wrapper -> wrapper
                    .like(User::getNickname, keyword)
                    .or()
                    .like(User::getUserId, keyword)
                    .or()
                    .like(User::getBio, keyword))
                .orderByDesc(User::getFansCount));
        
        Long currentUserId = UserContext.getUserId();
        List<UserResponse> userResponses = result.getRecords().stream()
            .map(user -> convertToUserResponse(user, currentUserId))
            .toList();
        
        return new PageResult<>(userResponses, page, limit, result.getTotal());
    }
    
    /**
     * 获取用户列表
     */
    public PageResult<UserResponse> getUsers(int page, int limit) {
        Page<User> pageParam = new Page<>(page, limit);
        IPage<User> result = userMapper.selectPage(pageParam,
            new LambdaQueryWrapper<User>()
                .eq(User::getIsActive, 1)
                .orderByDesc(User::getCreatedAt));
        
        Long currentUserId = UserContext.getUserId();
        List<UserResponse> userResponses = result.getRecords().stream()
            .map(user -> convertToUserResponse(user, currentUserId))
            .toList();
        
        return new PageResult<>(userResponses, page, limit, result.getTotal());
    }
    
    /**
     * 获取用户详情
     */
    public UserDTO getUserInfo(String userId) {
        User user = getUserByUserId(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        userDTO.setInterests(parseInterests(user.getInterests()));
        return userDTO;
    }
    
    /**
     * 获取用户个性标签
     */
    public PersonalityTagResponse getPersonalityTags(String userId) {
        User user = getUserByUserId(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        
        PersonalityTagResponse response = new PersonalityTagResponse();
        response.setZodiacSign(user.getZodiacSign());
        response.setMbti(user.getMbti());
        response.setEducation(user.getEducation());
        response.setMajor(user.getMajor());
        response.setInterests(parseInterests(user.getInterests()));
        
        return response;
    }
    
    /**
     * 获取用户的帖子
     */
    public PageResult<PostResponse> getUserPosts(String targetUserId, int page, int limit) {
        User targetUser = getUserByUserId(targetUserId);
        if (targetUser == null) {
            throw new BusinessException("用户不存在");
        }
        
        Long currentUserId = UserContext.getUserId();
        boolean isSelf = currentUserId != null && currentUserId.equals(targetUser.getId());
        
        Page<Post> pageParam = new Page<>(page, limit);
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
            .eq(Post::getUserId, targetUser.getId())
            .orderByDesc(Post::getCreatedAt);
        
        // 非本人只能看已发布的帖子
        if (!isSelf) {
            wrapper.eq(Post::getIsDraft, 0);
        }
        
        IPage<Post> result = postMapper.selectPage(pageParam, wrapper);
        
        List<PostResponse> postResponses = buildPostResponses(result.getRecords(), currentUserId);
        return new PageResult<>(postResponses, page, limit, result.getTotal());
    }
    
    /**
     * 获取用户的收藏
     */
    public PageResult<PostResponse> getUserCollections(String targetUserId, int page, int limit) {
        User targetUser = getUserByUserId(targetUserId);
        if (targetUser == null) {
            throw new BusinessException("用户不存在");
        }
        
        Long currentUserId = UserContext.getUserId();
        
        // 查询收藏记录
        Page<Collection> pageParam = new Page<>(page, limit);
        IPage<Collection> collectionPage = collectionMapper.selectPage(pageParam,
            new LambdaQueryWrapper<Collection>()
                .eq(Collection::getUserId, targetUser.getId())
                .orderByDesc(Collection::getCreatedAt));
        
        if (collectionPage.getRecords().isEmpty()) {
            return PageResult.empty(page, limit);
        }
        
        // 获取帖子ID列表
        List<Long> postIds = collectionPage.getRecords().stream()
            .map(Collection::getPostId)
            .toList();
        
        // 查询帖子
        List<Post> posts = postMapper.selectList(
            new LambdaQueryWrapper<Post>()
                .in(Post::getId, postIds)
                .eq(Post::getIsDraft, 0));
        
        List<PostResponse> postResponses = buildPostResponses(posts, currentUserId);
        return new PageResult<>(postResponses, page, limit, collectionPage.getTotal());
    }
    
    /**
     * 获取用户点赞的帖子
     */
    public PageResult<PostResponse> getUserLikes(String targetUserId, int page, int limit) {
        User targetUser = getUserByUserId(targetUserId);
        if (targetUser == null) {
            throw new BusinessException("用户不存在");
        }
        
        Long currentUserId = UserContext.getUserId();
        
        // 查询点赞记录（目标类型为帖子：1）
        Page<Like> pageParam = new Page<>(page, limit);
        IPage<Like> likePage = likeMapper.selectPage(pageParam,
            new LambdaQueryWrapper<Like>()
                .eq(Like::getUserId, targetUser.getId())
                .eq(Like::getTargetType, 1)
                .orderByDesc(Like::getCreatedAt));
        
        if (likePage.getRecords().isEmpty()) {
            return PageResult.empty(page, limit);
        }
        
        // 获取帖子ID列表
        List<Long> postIds = likePage.getRecords().stream()
            .map(Like::getTargetId)
            .toList();
        
        // 查询帖子
        List<Post> posts = postMapper.selectList(
            new LambdaQueryWrapper<Post>()
                .in(Post::getId, postIds)
                .eq(Post::getIsDraft, 0));
        
        List<PostResponse> postResponses = buildPostResponses(posts, currentUserId);
        return new PageResult<>(postResponses, page, limit, likePage.getTotal());
    }
    
    /**
     * 获取用户统计数据
     */
    public UserStatsResponse getUserStats(String userId) {
        User user = getUserByUserId(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        
        UserStatsResponse stats = new UserStatsResponse();
        
        // 帖子数
        Long postCount = postMapper.selectCount(
            new LambdaQueryWrapper<Post>()
                .eq(Post::getUserId, user.getId())
                .eq(Post::getIsDraft, 0));
        stats.setPostCount(postCount);
        
        // 关注数和粉丝数
        stats.setFollowCount(user.getFollowCount());
        stats.setFansCount(user.getFansCount());
        
        // 获赞数
        stats.setLikeCount(user.getLikeCount());
        
        // 收藏数
        long collectCount = collectionMapper.selectCount(
            new LambdaQueryWrapper<Collection>()
                .eq(Collection::getUserId, user.getId()));
        stats.setCollectCount(collectCount);
        
        // 获赞与收藏总数
        stats.setLikesAndCollects(user.getLikeCount() + collectCount);
        
        return stats;
    }
    
    /**
     * 更新用户资料
     */
    public UserDTO updateUser(String userId, UserDTO request) {
        User user = getUserByUserId(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        Long currentUserId = UserContext.getUserId();
        // 验证是本人
        if (!user.getId().equals(currentUserId)) {
            throw new BusinessException("无权修改他人资料");
        }
        
        // 更新字段
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }
        if (request.getZodiacSign() != null) {
            user.setZodiacSign(request.getZodiacSign());
        }
        if (request.getMbti() != null) {
            user.setMbti(request.getMbti());
        }
        if (request.getEducation() != null) {
            user.setEducation(request.getEducation());
        }
        if (request.getMajor() != null) {
            user.setMajor(request.getMajor());
        }
        if (request.getInterests() != null) {
            user.setInterests(JSONUtil.toJsonStr(request.getInterests()));
        }
        
        userMapper.updateById(user);

        // 更新用户信息时清除缓存
        String userInfoKey = RedisKeyUtil.getUserInfoKey(user.getId());
        cacheService.delete(userInfoKey);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        userDTO.setInterests(parseInterests(user.getInterests()));
        
        return userDTO;
    }
    
    /**
     * 修改密码
     */
    @Transactional
    public void changePassword(String userId, ChangePasswordRequest request, Long currentUserId) {
        User user = getUserByUserId(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        
        // 验证是本人
        if (!user.getId().equals(currentUserId)) {
            throw new BusinessException("无权修改他人密码");
        }
        
        // 验证当前密码（使用 PasswordEncoder）
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException("当前密码错误");
        }
        
        // 更新密码
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
    }
    
    /**
     * 删除账号
     */
    @Transactional
    public void deleteAccount(String userId, String password, Long currentUserId) {
        User user = getUserByUserId(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        
        // 验证是本人
        if (!user.getId().equals(currentUserId)) {
            throw new BusinessException("无权删除他人账号");
        }
        
        // 验证密码（使用 PasswordEncoder）
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException("密码错误");
        }
        
        // 软删除：设置为不活跃
        user.setIsActive(0);
        userMapper.updateById(user);
        
        // 清除缓存（id 缓存和Hash映射）
        cacheService.delete(RedisKeyUtil.getUserInfoKey(user.getId()));
        cacheService.deleteUsername2ID(user.getUserId());
    }
    
    // ============ 私有辅助方法 ============
    
    /**
     * 将User转换为UserResponse
     */
    private UserResponse convertToUserResponse(User user, Long currentUserId) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUserId(user.getUserId());
        response.setNickname(user.getNickname());
        response.setAvatar(user.getAvatar());
        response.setBio(user.getBio());
        response.setLocation(user.getLocation());
        response.setFollowCount(user.getFollowCount());
        response.setFansCount(user.getFansCount());
        response.setLikeCount(user.getLikeCount());
        response.setGender(user.getGender());
        response.setZodiacSign(user.getZodiacSign());
        response.setMbti(user.getMbti());
        response.setEducation(user.getEducation());
        response.setMajor(user.getMajor());
        response.setInterests(parseInterests(user.getInterests()));
        response.setVerified(user.getVerified());
        response.setCreatedAt(user.getCreatedAt());
        
        
        
        return response;
    }
    
    /**
     * 解析兴趣爱好JSON字符串
     */
    private List<String> parseInterests(String interestsJson) {
        if (interestsJson == null || interestsJson.isEmpty()) {
            return new ArrayList<>();
        }
        return JSONUtil.toList(interestsJson, String.class);
    }
    
    /**
     * 构建帖子响应列表
     */
    private List<PostResponse> buildPostResponses(List<Post> posts, Long currentUserId) {
        if (posts.isEmpty()) {
            return new ArrayList<>();
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
        return posts.stream()
            .map(post -> {
                PostResponse response = new PostResponse();
                response.setId(post.getId());
                response.setTitle(post.getTitle());
                response.setContent(post.getContent());
                response.setCategoryId(post.getCategoryId());
                response.setType(post.getType());
                response.setIsDraft(post.getIsDraft());
                response.setCreatedAt(post.getCreatedAt());
                response.setViewCount(post.getViewCount());
                response.setLikeCount(post.getLikeCount());
                response.setCollectCount(post.getCollectCount());
                response.setCommentCount(post.getCommentCount());
                
                // 分类
                Category category = categoryMap.get(post.getCategoryId());
                if (category != null) {
                    response.setCategory(category.getName());
                }
                
                // 图片
                List<String> images = imageMap.get(post.getId());
                response.setImages(images != null ? images : new ArrayList<>());
                if (images != null && !images.isEmpty()) {
                    response.setImage(images.get(0));
                }
                
                // 视频
                PostVideo video = videoMap.get(post.getId());
                if (video != null) {
                    response.setVideoUrl(video.getVideoUrl());
                    response.setCoverUrl(video.getCoverUrl());
                    response.setImage(video.getCoverUrl());
                    if (video.getCoverUrl() != null) {
                        response.getImages().addFirst(video.getCoverUrl());
                    }
                }
                
                // 标签
                response.setTags(postTagMap.getOrDefault(post.getId(), new ArrayList<>()));
                
                // 用户
                User user = userMap.get(post.getUserId());
                if (user != null) {
                    response.setUserId(user.getId());
                    response.setAuthorAutoId(user.getId());
                    response.setAuthorAccount(user.getUserId());
                    response.setNickname(user.getNickname());
                    response.setUserAvatar(user.getAvatar());
                    response.setLocation(user.getLocation());
                    response.setVerified(user.getVerified());
                }
                
                // 点赞和收藏状态
                response.setLiked(likedSet.contains(post.getId()));
                response.setCollected(collectedSet.contains(post.getId()));
                
                return response;
            })
            .toList();
    }
    
    /**
     * 根据小旅书号获取用户信息
     * 1. 先从Hash映射缓存获取 id
     * 2. 如果有 id，用 id 查用户信息缓存
     * 3. 如果没有 id，查数据库，并缓存映射关系
     */
    public User getUserByUserId(String userId) {
        // 1. 先尝试从Hash映射缓存获取数据库ID
        Long id = cacheService.username2ID(userId);
        
        if (id != null) {
            // 2. 有映射，直接用 id 查用户信息缓存
            String userInfoKey = RedisKeyUtil.getUserInfoKey(id);
            return cacheService.getOrLoad(userInfoKey, User.class, RedisExpireConstant.USER_INFO_EXPIRE, () ->
                userMapper.selectById(id)
            );
        }
        
        // 3. 没有映射缓存，查数据库
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUserId, userId));
        
        if (user == null) {
            // 用户不存在，缓存空值防止穿透
            cacheService.setUsername2Null(userId);
            return null;
        }
        
        // 4. 缓存映射关系到Hash
        cacheService.setUsername2ID(userId, user.getId());
        
        // 5. 缓存用户信息（使用 id 作为 key）
        cacheService.load(RedisKeyUtil.getUserInfoKey(user.getId()), user, RedisExpireConstant.USER_INFO_EXPIRE);
        
        return user;
    }
    
    /**
     * 根据数据库ID获取用户（推荐使用，缓存效率更高）
     */
    public User getUserById(Long id) {
        String userInfoKey = RedisKeyUtil.getUserInfoKey(id);
        return cacheService.getOrLoad(userInfoKey, User.class, RedisExpireConstant.USER_INFO_EXPIRE, () ->
            userMapper.selectById(id)
        );
    }
    
    // ============ 认证申请相关方法 ============
    
    /**
     * 提交认证申请
     *
     * @param request 认证申请请求
     * @return 审核ID
     */
    @Transactional
    public Long submitVerification(VerificationRequest request) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        
        // 验证认证类型
        if (request.getType() != 1 && request.getType() != 2) {
            throw new BusinessException("无效的认证类型");
        }
        
        // 检查是否已有待审核的认证申请
        com.xiaolvshu.entity.Audit existingAudit = auditMapper.selectOne(
                new LambdaQueryWrapper<com.xiaolvshu.entity.Audit>()
                        .eq(com.xiaolvshu.entity.Audit::getUserId, userId)
                        .eq(com.xiaolvshu.entity.Audit::getType, request.getType())
                        .eq(com.xiaolvshu.entity.Audit::getStatus, com.xiaolvshu.entity.Audit.STATUS_PENDING));
        
        if (existingAudit != null) {
            throw new BusinessException("您已有相同类型的认证申请正在审核中，请耐心等待");
        }
        
        // 创建审核记录
        com.xiaolvshu.entity.Audit audit = new com.xiaolvshu.entity.Audit();
        audit.setUserId(userId);
        audit.setType(request.getType());
        audit.setContent(request.getContent());
        audit.setStatus(com.xiaolvshu.entity.Audit.STATUS_PENDING);
        auditMapper.insert(audit);
        
        return audit.getId();
    }
    
    /**
     * 获取用户认证状态
     *
     * @return 认证状态列表
     */
    public List<VerificationStatusResponse> getVerificationStatus() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        
        List<com.xiaolvshu.entity.Audit> audits = auditMapper.selectList(
                new LambdaQueryWrapper<com.xiaolvshu.entity.Audit>()
                        .eq(com.xiaolvshu.entity.Audit::getUserId, userId)
                        .orderByDesc(com.xiaolvshu.entity.Audit::getCreatedAt));
        
        return audits.stream().map(audit -> {
            VerificationStatusResponse response = new VerificationStatusResponse();
            response.setId(audit.getId());
            response.setType(audit.getType());
            response.setStatus(audit.getStatus());
            response.setCreatedAt(audit.getCreatedAt());
            response.setAuditTime(audit.getAuditTime());
            return response;
        }).toList();
    }
    
    /**
     * 撤回认证申请
     */
    @Transactional
    public void revokeVerification() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        
        // 查找用户的认证申请（包括待审核、已通过和已拒绝的）
        List<com.xiaolvshu.entity.Audit> audits = auditMapper.selectList(
                new LambdaQueryWrapper<com.xiaolvshu.entity.Audit>()
                        .eq(com.xiaolvshu.entity.Audit::getUserId, userId)
                        .in(com.xiaolvshu.entity.Audit::getStatus, 0, 1, 2));
        
        if (audits.isEmpty()) {
            throw new BusinessException("没有找到可撤回的认证申请");
        }
        
        // 检查是否有已通过的认证
        boolean hasApproved = audits.stream()
                .anyMatch(audit -> audit.getStatus() == com.xiaolvshu.entity.Audit.STATUS_APPROVED);
        
        // 删除认证申请记录
        auditMapper.delete(new LambdaQueryWrapper<com.xiaolvshu.entity.Audit>()
                .eq(com.xiaolvshu.entity.Audit::getUserId, userId)
                .in(com.xiaolvshu.entity.Audit::getStatus, 0, 1, 2));
        
        // 如果撤回的是已通过的认证，需要将用户的verified字段重置为0
        if (hasApproved) {
            User user = userMapper.selectById(userId);
            if (user != null) {
                user.setVerified(0);
                userMapper.updateById(user);
            }
        }
    }
}
