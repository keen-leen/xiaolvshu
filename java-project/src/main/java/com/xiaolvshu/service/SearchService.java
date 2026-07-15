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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final SearchIndexService searchIndexService;

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
            SearchResponseItem<PostResponse> postResult = searchPostItems(keyword, tag, type, page, limit);
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

    /**
     * 供 /posts/search 使用的简化帖子搜索入口。
     * 与 /search 共用同一套 ES 检索和响应组装逻辑。
     */
    public PageResult<PostResponse> searchPosts(String keyword, int page, int limit) {
        String normalizedKeyword = keyword == null ? "" : keyword;
        SearchResponseItem<PostResponse> result = searchPostItems(normalizedKeyword, "", "all", page, limit);
        return new PageResult<>(result.getData(), page, limit, result.getPagination().getTotal());
    }

    /**
     * 检索笔记/视频，返回结果按 ES 排序，包含 tag 统计信息和分页信息。
     */
    private SearchResponseItem<PostResponse> searchPostItems(String keyword, String tag, String type, int page, int limit) {
        Integer postType = switch (type) {
            case "posts" -> 1;
            case "videos" -> 2;
            default -> null;
        };
        // 1) 调用 Elasticsearch 检索相关笔记ID
        SearchIndexService.PostSearchResult result = searchIndexService.searchPosts(keyword, tag, postType, page, limit);
        // 2) 根据检索结果的笔记ID列表查询数据库，获取完整笔记信息
        List<Post> posts = result.postIds().isEmpty()
                ? Collections.emptyList()
                : postMapper.selectBatchIds(result.postIds());
        Map<Long, Post> postMap = posts.stream().collect(Collectors.toMap(Post::getId, p -> p));
        List<Post> orderedPosts = result.postIds().stream()
                .map(postMap::get)
                .filter(Objects::nonNull)
                // ES 删除失败时可能短暂残留旧文档，业务库中的草稿状态必须最终兜底。
                .filter(post -> !Integer.valueOf(1).equals(post.getIsDraft()))
                .toList();

        List<TagStatsDTO> tagStats = result.tagCounts().entrySet().stream().map(entry -> {
            TagStatsDTO dto = new TagStatsDTO();
            dto.setId(entry.getKey());
            dto.setLabel(entry.getKey());
            dto.setCount(entry.getValue());
            return dto;
        }).toList();

        SearchResponseItem<PostResponse> response = new SearchResponseItem<>();
        response.setData(buildPostResponses(orderedPosts));
        response.setTagStats(tagStats);
        response.setPagination(new PaginationDTO(page, limit, result.total()));
        return response;
    }

    /**
     * 重要方法
     * 构建笔记查询响应，包括作者信息、图片、视频、标签、点赞/收藏状态等
     */
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
