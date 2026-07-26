package com.xiaolvshu.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaolvshu.dto.admin.*;
import com.xiaolvshu.entity.Collection;
import com.xiaolvshu.entity.Post;
import com.xiaolvshu.entity.User;
import com.xiaolvshu.exception.BusinessException;
import com.xiaolvshu.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端收藏管理控制器
 */
@RestController
@RequestMapping("/admin/collections")
@RequiredArgsConstructor
public class AdminCollectionController {

    private final CollectionService collectionService;
    private final UserService userService;
    private final PostService postService;

    /**
     * 分页查询收藏列表
     */
    @GetMapping
    public AdminResult<?> getCollectionList(@RequestParam(value = "user_display_id", required = false) String userDisplayId, @RequestParam(value = "post_id", required = false) Long postId, AdminCollectionQueryDTO queryDTO) {
        queryDTO.setUserDisplayId(userDisplayId);
        queryDTO.setPostId(postId);
        Page<Collection> pageParam = new Page<>(queryDTO.getPage(), queryDTO.getLimit());

        LambdaQueryWrapper<Collection> wrapper = new LambdaQueryWrapper<>();

        // 笔记ID搜索
        if (queryDTO.getPostId() != null) {
            wrapper.eq(Collection::getPostId, queryDTO.getPostId());
        }

        // 用户显示ID搜索
        if (queryDTO.getUserDisplayId() != null && !queryDTO.getUserDisplayId().trim().isEmpty()) {
            LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.like(User::getUserId, queryDTO.getUserDisplayId().trim());
            List<User> users = userService.list(userWrapper);
            if (users.isEmpty()) {
                return AdminResult.success(new ArrayList<>(), 0L, queryDTO.getPage(), queryDTO.getLimit());
            }
            List<Long> userIds = users.stream().map(User::getId).collect(Collectors.toList());
            wrapper.in(Collection::getUserId, userIds);
        }

        // 排序
        List<String> allowedSortFields = Arrays.asList("id", "user_id", "created_at");
        if (queryDTO.getSortField() != null && allowedSortFields.contains(queryDTO.getSortField())) {
            boolean isAsc = "asc".equalsIgnoreCase(queryDTO.getSortOrder());
            switch (queryDTO.getSortField()) {
                case "id":
                    wrapper.orderBy(true, isAsc, Collection::getId);
                    break;
                case "user_id":
                    wrapper.orderBy(true, isAsc, Collection::getUserId);
                    break;
                case "created_at":
                    wrapper.orderBy(true, isAsc, Collection::getCreatedAt);
                    break;
            }
        } else {
            wrapper.orderByDesc(Collection::getCreatedAt);
        }

        IPage<Collection> result = collectionService.page(pageParam, wrapper);

        List<AdminCollectionDTO> collectionDTOs = convertToDTOs(result.getRecords());

        return AdminResult.success(collectionDTOs, result.getTotal(), queryDTO.getPage(), queryDTO.getLimit());
    }

    /**
     * 批量组装当前页 DTO，查询次数不随当前页记录数增长。
     */
    List<AdminCollectionDTO> convertToDTOs(List<Collection> collections) {
        if (collections.isEmpty()) {
            return List.of();
        }

        Set<Long> userIds = collections.stream()
                .map(Collection::getUserId)
                .collect(Collectors.toSet());
        Set<Long> postIds = collections.stream()
                .map(Collection::getPostId)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Post> postMap = postService.listByIds(postIds).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));

        List<AdminCollectionDTO> collectionDTOs = new ArrayList<>(collections.size());
        for (Collection collection : collections) {
            AdminCollectionDTO dto = new AdminCollectionDTO();
            dto.setId(collection.getId());
            dto.setUserId(collection.getUserId());
            dto.setPostId(collection.getPostId());
            dto.setCreatedAt(collection.getCreatedAt());

            User user = userMap.get(collection.getUserId());
            if (user != null) {
                dto.setNickname(user.getNickname());
                dto.setUserDisplayId(user.getUserId() != null ? user.getUserId() : "user" + String.format("%03d", user.getId()));
            }

            Post post = postMap.get(collection.getPostId());
            if (post != null) {
                dto.setPostTitle(post.getTitle());
            }

            collectionDTOs.add(dto);
        }
        return collectionDTOs;
    }

    /**
     * 获取单个收藏详情
     */
    @GetMapping("/{id}")
    public AdminResult<Collection> getCollectionById(@PathVariable Long id) {
        Collection collection = collectionService.getById(id);
        if (collection == null) {
            return AdminResult.notFound("收藏不存在");
        }
        return AdminResult.success("操作成功", collection);
    }

    /**
     * 创建收藏
     */
    @PostMapping
    public AdminResult<Map<String, Long>> createCollection(@RequestBody Collection collection) {
        // 验证必填字段
        if (collection.getUserId() == null) {
            return AdminResult.badRequest("缺少必填字段: user_id");
        }
        if (collection.getPostId() == null) {
            return AdminResult.badRequest("缺少必填字段: post_id");
        }

        // 检查用户是否存在
        User user = userService.getById(collection.getUserId());
        if (user == null) {
            return AdminResult.badRequest("用户不存在");
        }

        // 检查笔记是否存在
        Post post = postService.getById(collection.getPostId());
        if (post == null) {
            return AdminResult.badRequest("笔记不存在");
        }

        // 检查是否已经收藏
        LambdaQueryWrapper<Collection> dupWrapper = new LambdaQueryWrapper<>();
        dupWrapper.eq(Collection::getUserId, collection.getUserId())
               .eq(Collection::getPostId, collection.getPostId());
        if (collectionService.count(dupWrapper) > 0) {
            return AdminResult.conflict("已经收藏过该笔记");
        }

        try {
            Collection created = collectionService.createForAdmin(collection.getUserId(), collection.getPostId());
            return AdminResult.success("收藏创建成功", Map.of("id", created.getId()));
        } catch (BusinessException exception) {
            // 并发请求可能同时通过前置查询，唯一索引冲突仍保持管理端既有 409 响应结构。
            return AdminResult.conflict(exception.getMessage());
        }
    }

    /**
     * 更新收藏
     */
    @PutMapping("/{id}")
    public AdminResult<Void> updateCollection(@PathVariable Long id, @RequestBody Collection collection) {
        Collection existingCollection = collectionService.getById(id);
        if (existingCollection == null) {
            return AdminResult.notFound("收藏不存在");
        }

        if (collection.getPostId() != null) {
            // 检查笔记是否存在
            Post post = postService.getById(collection.getPostId());
            if (post == null) {
                return AdminResult.badRequest("笔记不存在");
            }
            try {
                collectionService.updatePostForAdmin(id, collection.getPostId());
            } catch (BusinessException exception) {
                return AdminResult.conflict(exception.getMessage());
            }
        }
        return AdminResult.success("收藏更新成功");
    }

    /**
     * 删除单个收藏
     */
    @DeleteMapping("/{id}")
    public AdminResult<Void> deleteCollection(@PathVariable Long id) {
        Collection collection = collectionService.getById(id);
        if (collection == null) {
            return AdminResult.notFound("收藏不存在");
        }

        collectionService.deleteForAdmin(id);
        return AdminResult.success("收藏删除成功");
    }

    /**
     * 批量删除收藏
     */
    @DeleteMapping
    public AdminResult<Map<String, Integer>> deleteCollections(@RequestBody BatchDeleteDTO deleteDTO) {
        if (deleteDTO.getIds() == null || deleteDTO.getIds().isEmpty()) {
            return AdminResult.badRequest("缺少必填字段: ids");
        }

        int deletedCount = collectionService.deleteBatchForAdmin(deleteDTO.getIds());
        return AdminResult.success("成功删除" + deletedCount + "条收藏", Map.of("deletedCount", deletedCount));
    }
}
