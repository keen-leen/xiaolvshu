package com.xiaolvshu.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaolvshu.dto.admin.*;
import com.xiaolvshu.entity.*;
import com.xiaolvshu.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 管理端关注管理控制器
 */
@RestController
@RequestMapping("/admin/follows")
@RequiredArgsConstructor
public class AdminFollowController {

    private final FollowService followService;
    private final UserService userService;

    /**
     * 分页查询关注列表
     */
    @GetMapping
    public AdminResult<?> getFollowList(AdminFollowQueryDTO queryDTO) {
        Page<Follow> pageParam = new Page<>(queryDTO.getPage(), queryDTO.getLimit());
        
        LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<>();
        
        // 关注者显示ID搜索
        if (queryDTO.getFollowerDisplayId() != null && !queryDTO.getFollowerDisplayId().trim().isEmpty()) {
            LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.eq(User::getUserId, queryDTO.getFollowerDisplayId().trim());
            User user = userService.getOne(userWrapper);
            if (user == null) {
                return AdminResult.success(new ArrayList<>(), 0L, queryDTO.getPage(), queryDTO.getLimit());
            }
            wrapper.eq(Follow::getFollowerId, user.getId());
        }
        
        // 被关注者显示ID搜索
        if (queryDTO.getFollowingDisplayId() != null && !queryDTO.getFollowingDisplayId().trim().isEmpty()) {
            LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.eq(User::getUserId, queryDTO.getFollowingDisplayId().trim());
            User user = userService.getOne(userWrapper);
            if (user == null) {
                return AdminResult.success(new ArrayList<>(), 0L, queryDTO.getPage(), queryDTO.getLimit());
            }
            wrapper.eq(Follow::getFollowingId, user.getId());
        }
        
        // 排序
        List<String> allowedSortFields = Arrays.asList("id", "follower_id", "following_id", "created_at");
        if (queryDTO.getSortField() != null && allowedSortFields.contains(queryDTO.getSortField())) {
            boolean isAsc = "asc".equalsIgnoreCase(queryDTO.getSortOrder());
            switch (queryDTO.getSortField()) {
                case "id":
                    wrapper.orderBy(true, isAsc, Follow::getId);
                    break;
                case "follower_id":
                    wrapper.orderBy(true, isAsc, Follow::getFollowerId);
                    break;
                case "following_id":
                    wrapper.orderBy(true, isAsc, Follow::getFollowingId);
                    break;
                case "created_at":
                    wrapper.orderBy(true, isAsc, Follow::getCreatedAt);
                    break;
            }
        } else {
            wrapper.orderByDesc(Follow::getCreatedAt);
        }
        
        IPage<Follow> result = followService.page(pageParam, wrapper);
        
        // 转换为DTO
        List<AdminFollowDTO> followDTOs = new ArrayList<>();
        for (Follow follow : result.getRecords()) {
            AdminFollowDTO dto = new AdminFollowDTO();
            dto.setId(follow.getId());
            dto.setFollowerId(follow.getFollowerId());
            dto.setFollowingId(follow.getFollowingId());
            dto.setCreatedAt(follow.getCreatedAt());
            
            // 获取关注者信息
            User follower = userService.getById(follow.getFollowerId());
            if (follower != null) {
                dto.setFollowerNickname(follower.getNickname());
                dto.setFollowerDisplayId(follower.getUserId() != null ? follower.getUserId() : "user" + String.format("%03d", follower.getId()));
            }
            
            // 获取被关注者信息
            User following = userService.getById(follow.getFollowingId());
            if (following != null) {
                dto.setFollowingNickname(following.getNickname());
                dto.setFollowingDisplayId(following.getUserId() != null ? following.getUserId() : "user" + String.format("%03d", following.getId()));
            }
            
            followDTOs.add(dto);
        }
        
        return AdminResult.success(followDTOs, result.getTotal(), queryDTO.getPage(), queryDTO.getLimit());
    }

    /**
     * 获取单个关注详情
     */
    @GetMapping("/{id}")
    public AdminResult<Follow> getFollowById(@PathVariable Long id) {
        Follow follow = followService.getById(id);
        if (follow == null) {
            return AdminResult.notFound("关注不存在");
        }
        return AdminResult.success("操作成功", follow);
    }

    /**
     * 创建关注
     */
    @PostMapping
    public AdminResult<Map<String, Long>> createFollow(@RequestBody Follow follow) {
        // 验证必填字段
        if (follow.getFollowerId() == null) {
            return AdminResult.badRequest("缺少必填字段: follower_id");
        }
        if (follow.getFollowingId() == null) {
            return AdminResult.badRequest("缺少必填字段: following_id");
        }
        
        // 不能关注自己
        if (follow.getFollowerId().equals(follow.getFollowingId())) {
            return AdminResult.badRequest("不能关注自己");
        }
        
        // 检查用户是否存在
        User follower = userService.getById(follow.getFollowerId());
        if (follower == null) {
            return AdminResult.badRequest("关注者不存在");
        }
        
        User following = userService.getById(follow.getFollowingId());
        if (following == null) {
            return AdminResult.badRequest("被关注者不存在");
        }
        
        // 检查是否已经关注
        LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Follow::getFollowerId, follow.getFollowerId())
               .eq(Follow::getFollowingId, follow.getFollowingId());
        if (followService.count(wrapper) > 0) {
            return AdminResult.conflict("已经关注过了");
        }
        
        followService.save(follow);
        return AdminResult.success("关注创建成功", Map.of("id", follow.getId()));
    }

    /**
     * 更新关注
     */
    @PutMapping("/{id}")
    public AdminResult<Void> updateFollow(@PathVariable Long id, @RequestBody Follow follow) {
        Follow existingFollow = followService.getById(id);
        if (existingFollow == null) {
            return AdminResult.notFound("关注不存在");
        }
        
        if (follow.getFollowingId() != null) {
            // 不能关注自己
            if (existingFollow.getFollowerId().equals(follow.getFollowingId())) {
                return AdminResult.badRequest("不能关注自己");
            }
            
            // 检查被关注者是否存在
            User following = userService.getById(follow.getFollowingId());
            if (following == null) {
                return AdminResult.badRequest("被关注者不存在");
            }
            existingFollow.setFollowingId(follow.getFollowingId());
        }
        
        followService.updateById(existingFollow);
        return AdminResult.success("关注更新成功");
    }

    /**
     * 删除单个关注
     */
    @DeleteMapping("/{id}")
    public AdminResult<Void> deleteFollow(@PathVariable Long id) {
        Follow follow = followService.getById(id);
        if (follow == null) {
            return AdminResult.notFound("关注不存在");
        }
        
        followService.removeById(id);
        return AdminResult.success("关注删除成功");
    }

    /**
     * 批量删除关注
     */
    @DeleteMapping
    public AdminResult<Map<String, Integer>> deleteFollows(@RequestBody BatchDeleteDTO deleteDTO) {
        if (deleteDTO.getIds() == null || deleteDTO.getIds().isEmpty()) {
            return AdminResult.badRequest("缺少必填字段: ids");
        }
        
        int deletedCount = followService.removeByIds(deleteDTO.getIds()) ? deleteDTO.getIds().size() : 0;
        return AdminResult.success("成功删除" + deletedCount + "条关注", Map.of("deletedCount", deletedCount));
    }
}
