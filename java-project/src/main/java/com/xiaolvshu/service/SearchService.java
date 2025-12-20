package com.xiaolvshu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaolvshu.context.UserContext;
import com.xiaolvshu.dto.*;
import com.xiaolvshu.dto.SearchResponse.SearchResponseItem;
import com.xiaolvshu.entity.*;
import com.xiaolvshu.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final TagMapper tagMapper;
    private final PostTagMapper postTagMapper;
    private final PostImageMapper postImageMapper;
    private final PostVideoMapper postVideoMapper;
    private final LikeMapper likeMapper;
    private final CollectionMapper collectionMapper;
    private final FollowMapper followMapper;

    private final PostService postService;

    public SearchResponse search(SearchRequest request) {
        String keyword = request.getKeyword() == null ? "" : request.getKeyword();
        String tag = request.getTag() == null ? "" : request.getTag();
        String type = request.getType() == null || request.getType().isBlank() ? "all" : request.getType();

        Integer page = request.getPage();
        Integer limit = request.getLimit();

        SearchResponse response = new SearchResponse();
        response.setKeyword(keyword);
        response.setTag(tag);
        response.setType(type);
        // 如果既没有关键词也没有标签，返回空结果
        if (keyword.isBlank() && tag.isBlank()) {
            response.setData(Collections.emptyList());
            response.setTagStats(Collections.emptyList());
            PaginationDTO pagination = new PaginationDTO(page, limit, 0L);
            response.setPagination(pagination);
            return response;
        }
        // 全部/图文/视频
        if ("all".equals(type) || "posts".equals(type) || "videos".equals(type)) {
            SearchResponseItem<PostResponse> postResult = searchPosts(keyword, tag, type, page, limit);
            if ("all".equals(type)) {
                response.setData(postResult.getData());
                response.setTagStats(postResult.getTagStats());
                response.setPagination(postResult.getPagination());
            } else {
                response.setPosts(postResult);
            }
        }

        if ("users".equals(type)) {
            SearchResponseItem<UserResponse> users = searchUsers(keyword, page, limit);
            response.setUsers(users);
        }

        return response;
    }

    private SearchResponseItem<PostResponse> searchPosts(String keyword, String tag, String type, int page, int limit) {
        // 1) 构建 keyword 命中笔记集合（标题/内容/用户昵称/小旅书号/标签名）
        Set<Long> matchedPostIdsByKeyword = null;
        if (!keyword.isBlank()) {
            matchedPostIdsByKeyword = new HashSet<>();

            // 标题/内容
            List<Post> titleContent = postMapper.selectList(new LambdaQueryWrapper<Post>()
                    .eq(Post::getIsDraft, 0)
                    .and(w -> w.like(Post::getTitle, keyword).or().like(Post::getContent, keyword))
                    .select(Post::getId));
            matchedPostIdsByKeyword.addAll(titleContent.stream().map(Post::getId).toList());

            // 用户昵称/小旅书号
            List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                    .like(User::getNickname, keyword).or().like(User::getUserId, keyword)
                    .select(User::getId));
            if (!users.isEmpty()) {
                List<Long> userIds = users.stream().map(User::getId).toList();
                List<Post> byUsers = postMapper.selectList(new LambdaQueryWrapper<Post>()
                        .eq(Post::getIsDraft, 0)
                        .in(Post::getUserId, userIds)
                        .select(Post::getId));
                matchedPostIdsByKeyword.addAll(byUsers.stream().map(Post::getId).toList());
            }

            // 标签名称 like keyword
            List<Tag> tagsLike = tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                    .like(Tag::getName, keyword)
                    .select(Tag::getId));
            if (!tagsLike.isEmpty()) {
                List<Integer> tagIds = tagsLike.stream().map(Tag::getId).toList();
                List<PostTag> pts = postTagMapper.selectList(new LambdaQueryWrapper<PostTag>()
                        .in(PostTag::getTagId, tagIds)
                        .select(PostTag::getPostId));
                matchedPostIdsByKeyword.addAll(pts.stream().map(PostTag::getPostId).toList());
            }
        }

        // 2) tag 精确筛选帖子集合
        Set<Long> matchedPostIdsByTag = null;
        if (!tag.isBlank()) {
            Tag exact = tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName, tag));
            if (exact == null) {
                matchedPostIdsByTag = Collections.emptySet();
            } else {
                List<PostTag> pts = postTagMapper.selectList(new LambdaQueryWrapper<PostTag>()
                        .eq(PostTag::getTagId, exact.getId())
                        .select(PostTag::getPostId));
                matchedPostIdsByTag = new HashSet<>(pts.stream().map(PostTag::getPostId).toList());
            }
        }

        // 3) 合并条件：keyword + tag 都有则取交集，否则取对应集合
        Set<Long> finalPostIds = Collections.emptySet();
        if (matchedPostIdsByKeyword != null && matchedPostIdsByTag != null) {
            finalPostIds = new HashSet<>(matchedPostIdsByKeyword);
            finalPostIds.retainAll(matchedPostIdsByTag);
        } else if (matchedPostIdsByKeyword != null) {
            finalPostIds = matchedPostIdsByKeyword;
        } else if (matchedPostIdsByTag != null) {
            finalPostIds = matchedPostIdsByTag;
        }

        // 4) 类型过滤
        Integer postType = null;
        if ("posts".equals(type)) {
            postType = 1;
        } else if ("videos".equals(type)) {
            postType = 2;
        }

        // 5) 分页查询帖子
        List<PostResponse> data;
        long total;
        if (finalPostIds.isEmpty()) {
            data = Collections.emptyList();
            total = 0;
        } else {
            Page<Post> pageParam = new Page<>(page, limit);
            var result = postMapper.selectPage(pageParam, new LambdaQueryWrapper<Post>()
                    .eq(Post::getIsDraft, 0)
                    .in(Post::getId, finalPostIds)
                    .eq(postType != null, Post::getType, postType)
                    .orderByDesc(Post::getCreatedAt));

            total = result.getTotal();
            List<Post> posts = result.getRecords();
            data = buildPostResponses(posts);
        }

        // 6) tagStats：始终基于 keyword 搜索结果（不受 tag 筛选影响）
        List<TagStatsDTO> tagStats = keyword.isBlank() ? Collections.emptyList() : computeTagStatsForKeyword(keyword);

        PaginationDTO pagination = new PaginationDTO(page, limit, total);
        SearchResponseItem<PostResponse> resp = new SearchResponseItem<>();
        resp.setData(data);
        resp.setTagStats(tagStats);
        resp.setPagination(pagination);
        return resp;
    }

    private List<TagStatsDTO> computeTagStatsForKeyword(String keyword) {
        // 复用 keyword 命中帖子逻辑：这里取“keyword命中帖子集合”并统计其标签
        Set<Long> keywordPostIds = new HashSet<>();

        List<Post> titleContent = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .eq(Post::getIsDraft, 0)
                .and(w -> w.like(Post::getTitle, keyword).or().like(Post::getContent, keyword))
                .select(Post::getId));
        keywordPostIds.addAll(titleContent.stream().map(Post::getId).toList());

        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .like(User::getNickname, keyword)
                .or()
                .like(User::getUserId, keyword)
                .select(User::getId));
        if (!users.isEmpty()) {
            List<Long> userIds = users.stream().map(User::getId).toList();
            List<Post> byUsers = postMapper.selectList(new LambdaQueryWrapper<Post>()
                    .eq(Post::getIsDraft, 0)
                    .in(Post::getUserId, userIds)
                    .select(Post::getId));
            keywordPostIds.addAll(byUsers.stream().map(Post::getId).toList());
        }

        List<Tag> tagsLike = tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .like(Tag::getName, keyword)
                .select(Tag::getId));
        if (!tagsLike.isEmpty()) {
            List<Integer> tagIds = tagsLike.stream().map(Tag::getId).toList();
            List<PostTag> pts = postTagMapper.selectList(new LambdaQueryWrapper<PostTag>()
                    .in(PostTag::getTagId, tagIds)
                    .select(PostTag::getPostId));
            keywordPostIds.addAll(pts.stream().map(PostTag::getPostId).toList());
        }

        if (keywordPostIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<PostTag> pts = postTagMapper.selectList(new LambdaQueryWrapper<PostTag>()
                .in(PostTag::getPostId, keywordPostIds)
                .select(PostTag::getTagId));
        if (pts.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Integer, Long> counts = new HashMap<>();
        for (PostTag pt : pts) {
            Integer tagId = pt.getTagId();
            if (tagId == null) {
                continue;
            }
            counts.put(tagId, counts.getOrDefault(tagId, 0L) + 1L);
        }

        List<Integer> tagIds = new ArrayList<>(counts.keySet());
        Map<Integer, Tag> tagMap = tagMapper.selectBatchIds(tagIds).stream().collect(Collectors.toMap(Tag::getId, t -> t));

        return counts.entrySet().stream()
                .map(e -> {
                    Tag tag = tagMap.get(e.getKey());
                    if (tag == null) {
                        return null;
                    }
                    TagStatsDTO dto = new TagStatsDTO();
                    dto.setId(tag.getName());
                    dto.setLabel(tag.getName());
                    dto.setCount(e.getValue());
                    return dto;
                })
                .filter(x -> x != null)
                .sorted(Comparator.comparing(TagStatsDTO::getLabel))
                .limit(10)
                .toList();
    }

    private List<PostResponse> buildPostResponses(List<Post> posts) {
        if (posts == null || posts.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> postIds = posts.stream().map(Post::getId).toList();

        Map<Long, List<String>> imageMap = postImageMapper.selectList(
                new LambdaQueryWrapper<PostImage>().in(PostImage::getPostId, postIds))
                .stream()
                .collect(Collectors.groupingBy(
                        PostImage::getPostId,
                        Collectors.mapping(PostImage::getImageUrl, Collectors.toList())));

        Map<Long, PostVideo> videoMap = postVideoMapper.selectList(
                new LambdaQueryWrapper<PostVideo>().in(PostVideo::getPostId, postIds))
                .stream()
                .collect(Collectors.toMap(PostVideo::getPostId, v -> v, (v1, v2) -> v1));

        List<PostTag> postTags = postTagMapper.selectList(
                new LambdaQueryWrapper<PostTag>().in(PostTag::getPostId, postIds));
        List<Integer> tagIds = postTags.stream().map(PostTag::getTagId).distinct().toList();
        Map<Integer, Tag> tagMap = tagIds.isEmpty() ? Collections.emptyMap() : tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, t -> t));
        Map<Long, List<TagDTO>> postTagMap = postTags.stream()
                .collect(Collectors.groupingBy(
                        PostTag::getPostId,
                        Collectors.mapping(pt -> {
                            Tag tag = tagMap.get(pt.getTagId());
                            if (tag == null) {
                                return null;
                            }
                            TagDTO dto = new TagDTO();
                            dto.setId(tag.getId());
                            dto.setName(tag.getName());
                            return dto;
                        }, Collectors.filtering(t -> t != null, Collectors.toList()))));

        List<Long> userIds = posts.stream().map(Post::getUserId).distinct().toList();
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Long currentUserId = UserContext.getUserId();
        final Set<Long> likedSet;
        final Set<Long> collectedSet;
        if (currentUserId != null) {
            likedSet = likeMapper.selectList(new LambdaQueryWrapper<Like>()
                    .eq(Like::getUserId, currentUserId)
                    .eq(Like::getTargetType, 1)
                    .in(Like::getTargetId, postIds))
                    .stream()
                    .map(Like::getTargetId)
                    .collect(Collectors.toSet());

            collectedSet = collectionMapper.selectList(new LambdaQueryWrapper<Collection>()
                    .eq(Collection::getUserId, currentUserId)
                    .in(Collection::getPostId, postIds))
                    .stream()
                    .map(Collection::getPostId)
                    .collect(Collectors.toSet());
        } else {
            likedSet = Collections.emptySet();
            collectedSet = Collections.emptySet();
        }

        return posts.stream()
                .map(post -> postService.convertToResponseForSearch(
                        post,
                        userMap.get(post.getUserId()),
                        imageMap.get(post.getId()),
                        videoMap.get(post.getId()),
                        postTagMap.get(post.getId()),
                        likedSet.contains(post.getId()),
                        collectedSet.contains(post.getId())))
                .toList();
    }

    private SearchResponseItem<UserResponse> searchUsers(String keyword, int page, int limit) {
        Page<User> pageParam = new Page<>(page, limit);
        var result = userMapper.selectPage(pageParam, new LambdaQueryWrapper<User>()
                .and(w -> w.like(User::getNickname, keyword).or().like(User::getUserId, keyword))
                .orderByDesc(User::getCreatedAt));

        List<User> users = result.getRecords();
        if (users.isEmpty()) {
            return null;
        }

        List<Long> userIds = users.stream().map(User::getId).toList();

        Long currentUserId = UserContext.getUserId();
        Set<Long> followingSetTmp = Collections.emptySet();
        Set<Long> followerSetTmp = Collections.emptySet();
        if (currentUserId != null) {
            followingSetTmp = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getFollowerId, currentUserId)
                    .in(Follow::getFollowingId, userIds)
                    .select(Follow::getFollowingId))
                    .stream()
                    .map(Follow::getFollowingId)
                    .collect(Collectors.toSet());

            followerSetTmp = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getFollowingId, currentUserId)
                    .in(Follow::getFollowerId, userIds)
                    .select(Follow::getFollowerId))
                    .stream()
                    .map(Follow::getFollowerId)
                    .collect(Collectors.toSet());
        }

        final Set<Long> followingSet = followingSetTmp;
        final Set<Long> followerSet = followerSetTmp;

        List<UserResponse> data = users.stream().map(u -> {
            UserResponse dto = new UserResponse();
            dto.setId(u.getId());
            dto.setUserId(u.getUserId());
            dto.setNickname(u.getNickname());
            dto.setAvatar(u.getAvatar());
            dto.setBio(u.getBio());
            dto.setLocation(u.getLocation());
            dto.setFollowCount(u.getFollowCount());
            dto.setFansCount(u.getFansCount());
            dto.setLikeCount(u.getLikeCount());
            dto.setPostCount(u.getPostCount());
            dto.setGender(u.getGender());
            dto.setZodiacSign(u.getZodiacSign());
            dto.setMbti(u.getMbti());
            dto.setEducation(u.getEducation());
            dto.setMajor(u.getMajor());
            dto.setVerified(u.getVerified());
            dto.setCreatedAt(u.getCreatedAt());

            boolean isFollowing = currentUserId != null && followingSet.contains(u.getId());
            boolean isFollowed = currentUserId != null && followerSet.contains(u.getId());
            dto.setIsFollowing(isFollowing);
            dto.setIsFollowed(isFollowed);
            dto.setIsMutual(isFollowing && isFollowed);
            return dto;
        }).toList();

        return new SearchResponseItem<UserResponse>() {{
            setData(data);
            setPagination(new PaginationDTO(page, limit, result.getTotal()));
        }};
    }
}
