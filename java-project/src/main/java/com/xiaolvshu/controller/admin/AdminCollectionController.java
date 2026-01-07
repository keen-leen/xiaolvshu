package com.xiaolvshu.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaolvshu.dto.admin.*;
import com.xiaolvshu.entity.Collection;
import com.xiaolvshu.entity.Post;
import com.xiaolvshu.entity.User;
import com.xiaolvshu.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
        
        // 转换为DTO
        List<AdminCollectionDTO> collectionDTOs = new ArrayList<>();
        for (Collection collection : result.getRecords()) {
            AdminCollectionDTO dto = new AdminCollectionDTO();
            dto.setId(collection.getId());
            dto.setUserId(collection.getUserId());
            dto.setPostId(collection.getPostId());
            dto.setCreatedAt(collection.getCreatedAt());
            
            // 获取用户信息
            User user = userService.getById(collection.getUserId());
            if (user != null) {
                dto.setNickname(user.getNickname());
                dto.setUserDisplayId(user.getUserId() != null ? user.getUserId() : "user" + String.format("%03d", user.getId()));
            }
            
            // 获取笔记信息
            Post post = postService.getById(collection.getPostId());
            if (post != null) {
                dto.setPostTitle(post.getTitle());
            }
            
            collectionDTOs.add(dto);
        }
        
        return AdminResult.success(collectionDTOs, result.getTotal(), queryDTO.getPage(), queryDTO.getLimit());
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
        
        collectionService.save(collection);
        return AdminResult.success("收藏创建成功", Map.of("id", collection.getId()));
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
            existingCollection.setPostId(collection.getPostId());
        }
        
        collectionService.updateById(existingCollection);
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
        
        collectionService.removeById(id);
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
        
        int deletedCount = collectionService.removeByIds(deleteDTO.getIds()) ? deleteDTO.getIds().size() : 0;
        return AdminResult.success("成功删除" + deletedCount + "条收藏", Map.of("deletedCount", deletedCount));
    }
}
