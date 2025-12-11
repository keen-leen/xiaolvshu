package com.xiaolvshu.controller;

import com.xiaolvshu.common.Result;
import com.xiaolvshu.context.UserContext;
import com.xiaolvshu.dto.*;
import com.xiaolvshu.entity.User;
import com.xiaolvshu.service.FollowService;
import com.xiaolvshu.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserService userService;
    private final FollowService followService;
    
    /**
     * 搜索用户
     * GET /users/search?keyword=xxx&page=1&limit=20
     */
    @GetMapping("/search")
    public Result<Map<String, Object>> searchUsers(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        log.info("搜索用户: keyword={}, page={}, limit={}", keyword, page, limit);
        
        PageResult<UserResponse> result = userService.searchUsers(keyword, page, limit);
        
        Map<String, Object> data = new HashMap<>();
        data.put("users", result.getList());
        data.put("pagination", result.getPagination());
        
        return Result.success("获取成功", data);
    }
    
    /**
     * 获取用户个性标签
     * GET /users/:id/personality-tags
     */
    @GetMapping("/{userId}/personality-tags")
    public Result<PersonalityTagResponse> getPersonalityTags(@PathVariable String userId) {
        log.info("获取用户个性标签: userId={}", userId);
        PersonalityTagResponse tags = userService.getPersonalityTags(userId);
        return Result.success("获取成功", tags);
    }
    
    /**
     * 获取用户详情
     * GET /users/{userId}
     */
    @GetMapping("/{userId}")
    public Result<UserDTO> getUserInfo(@PathVariable String userId) {
        log.info("获取用户详情: userId={}", userId);
        UserDTO userDTO = userService.getUserInfo(userId);
        return Result.success("获取成功", userDTO);
    }
    
    /**
     * 获取用户列表
     * GET /users?page=1&limit=20
     */
    @GetMapping
    public Result<Map<String, Object>> getUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        log.info("获取用户列表: page={}, limit={}", page, limit);
        
        PageResult<UserResponse> result = userService.getUsers(page, limit);
        
        Map<String, Object> data = new HashMap<>();
        data.put("users", result.getList());
        data.put("pagination", result.getPagination());
        
        return Result.success("获取成功", data);
    }
    
    /**
     * 获取用户的帖子
     * GET /users/:id/posts?page=1&limit=20
     */
    @GetMapping("/{userId}/posts")
    public Result<Map<String, Object>> getUserPosts(
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        log.info("获取用户帖子: userId={}, page={}, limit={}", userId, page, limit);
        
        PageResult<PostResponse> result = userService.getUserPosts(userId, page, limit);
        
        Map<String, Object> data = new HashMap<>();
        data.put("posts", result.getList());
        data.put("pagination", result.getPagination());
        
        return Result.success("获取成功", data);
    }
    
    /**
     * 获取用户的收藏
     * GET /users/:id/collections?page=1&limit=20
     */
    @GetMapping("/{userId}/collections")
    public Result<Map<String, Object>> getUserCollections(
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        log.info("获取用户收藏: userId={}, page={}, limit={}", userId, page, limit);
        
        PageResult<PostResponse> result = userService.getUserCollections(userId, page, limit);
        
        Map<String, Object> data = new HashMap<>();
        data.put("posts", result.getList());
        data.put("pagination", result.getPagination());
        
        return Result.success("获取成功", data);
    }
    
    /**
     * 获取用户点赞的帖子
     * GET /users/:id/likes?page=1&limit=20
     */
    @GetMapping("/{userId}/likes")
    public Result<Map<String, Object>> getUserLikes(
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        log.info("获取用户点赞: userId={}, page={}, limit={}", userId, page, limit);
        
        PageResult<PostResponse> result = userService.getUserLikes(userId, page, limit);
        
        Map<String, Object> data = new HashMap<>();
        data.put("posts", result.getList());
        data.put("pagination", result.getPagination());
        
        return Result.success("获取成功", data);
    }
    
    /**
     * 关注用户
     * POST /users/:id/follow
     */
    @PostMapping("/{userId}/follow")
    public Result<Void> followUser(@PathVariable String userId) {
        log.info("关注用户: targetUserId={}", userId);
        
        Long currentUserId = UserContext.getUserId();
        User targetUser = userService.getUserByUserId(userId);
        
        if (targetUser == null) {
            return Result.notFound("用户不存在");
        }
        
        followService.follow(currentUserId, targetUser.getId());
        return Result.success("关注成功", null);
    }
    
    /**
     * 取消关注
     * DELETE /users/:id/follow
     */
    @DeleteMapping("/{userId}/follow")
    public Result<Void> unfollowUser(@PathVariable String userId) {
        log.info("取消关注: targetUserId={}", userId);
        
        Long currentUserId = UserContext.getUserId();
        User targetUser = userService.getUserByUserId(userId);
        
        if (targetUser == null) {
            return Result.notFound("用户不存在");
        }
        
        followService.unfollow(currentUserId, targetUser.getId());
        return Result.success("取消关注成功", null);
    }
    
    /**
     * 获取关注状态
     * GET /users/:id/follow-status
     */
    @GetMapping("/{userId}/follow-status")
    public Result<FollowStatusResponse> getFollowStatus(@PathVariable String userId) {
        log.info("获取关注状态: targetUserId={}", userId);
        
        Long currentUserId = UserContext.getUserId();
        User targetUser = userService.getUserByUserId(userId);
        
        if (targetUser == null) {
            return Result.notFound("用户不存在");
        }
        
        FollowStatusResponse status = followService.getFollowStatus(currentUserId, targetUser.getId());
        return Result.success("获取成功", status);
    }
    
    /**
     * 获取关注列表
     * GET /users/:id/following?page=1&limit=20
     */
    @GetMapping("/{userId}/following")
    public Result<Map<String, Object>> getFollowing(
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        log.info("获取关注列表: userId={}, page={}, limit={}", userId, page, limit);
        
        User targetUser = userService.getUserByUserId(userId);
        if (targetUser == null) {
            return Result.notFound("用户不存在");
        }
        
        Long currentUserId = UserContext.getUserId();
        PageResult<FollowUserResponse> result = followService.getFollowing(
            targetUser.getId(), currentUserId, page, limit);
        
        Map<String, Object> data = new HashMap<>();
        data.put("following", result.getList());
        data.put("pagination", result.getPagination());
        
        return Result.success("获取成功", data);
    }
    
    /**
     * 获取粉丝列表
     * GET /users/:id/followers?page=1&limit=20
     */
    @GetMapping("/{userId}/followers")
    public Result<Map<String, Object>> getFollowers(
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        log.info("获取粉丝列表: userId={}, page={}, limit={}", userId, page, limit);
        
        User targetUser = userService.getUserByUserId(userId);
        if (targetUser == null) {
            return Result.notFound("用户不存在");
        }
        
        Long currentUserId = UserContext.getUserId();
        PageResult<FollowUserResponse> result = followService.getFollowers(
            targetUser.getId(), currentUserId, page, limit);
        
        Map<String, Object> data = new HashMap<>();
        data.put("followers", result.getList());
        data.put("pagination", result.getPagination());
        
        return Result.success("获取成功", data);
    }
    
    /**
     * 获取互关列表
     * GET /users/:id/mutual-follows?page=1&limit=20
     */
    @GetMapping("/{userId}/mutual-follows")
    public Result<Map<String, Object>> getMutualFollows(
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        log.info("获取互关列表: userId={}, page={}, limit={}", userId, page, limit);
        
        User targetUser = userService.getUserByUserId(userId);
        if (targetUser == null) {
            return Result.notFound("用户不存在");
        }
        
        Long currentUserId = UserContext.getUserId();
        PageResult<FollowUserResponse> result = followService.getMutualFollows(
            targetUser.getId(), currentUserId, page, limit);
        
        Map<String, Object> data = new HashMap<>();
        data.put("mutualFollows", result.getList());
        data.put("pagination", result.getPagination());
        
        return Result.success("获取成功", data);
    }
    
    /**
     * 获取用户统计数据
     * GET /users/:id/stats
     */
    @GetMapping("/{userId}/stats")
    public Result<UserStatsResponse> getUserStats(@PathVariable String userId) {
        log.info("获取用户统计: userId={}", userId);
        UserStatsResponse stats = userService.getUserStats(userId);
        return Result.success("获取成功", stats);
    }
    
    /**
     * 更新用户资料
     * PUT /users/{userId}
     */
    @PutMapping("/{userId}")
    public Result<UserDTO> updateUser(@PathVariable String userId, @RequestBody UserDTO request) {
        log.info("更新用户资料: userId={}", userId);
        
        UserDTO user = userService.updateUser(userId, request);
        return Result.success("更新成功", user);
    }
    
    /**
     * 修改密码
     * PUT /users/:id/password
     */
    @PutMapping("/{userId}/password")
    public Result<Void> changePassword(
            @PathVariable String userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        log.info("修改密码: userId={}", userId);
        
        Long currentUserId = UserContext.getUserId();
        userService.changePassword(userId, request, currentUserId);
        return Result.success("密码修改成功", null);
    }
    
    /**
     * 删除账号
     * DELETE /users/:id
     */
    @DeleteMapping("/{userId}")
    public Result<Void> deleteAccount(
            @PathVariable String userId,
            @RequestParam String password) {
        log.info("删除账号: userId={}", userId);
        
        Long currentUserId = UserContext.getUserId();
        userService.deleteAccount(userId, password, currentUserId);
        return Result.success("账号已删除", null);
    }
}
