package com.xiaolvshu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaolvshu.dto.FollowStatusResponse;
import com.xiaolvshu.dto.FollowUserResponse;
import com.xiaolvshu.dto.PageResult;
import com.xiaolvshu.entity.Follow;
import com.xiaolvshu.entity.User;
import com.xiaolvshu.exception.BusinessException;
import com.xiaolvshu.mapper.FollowMapper;
import com.xiaolvshu.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关注服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowService extends ServiceImpl<FollowMapper, Follow> {
    
    private final FollowMapper followMapper;
    private final UserMapper userMapper;
    
    /**
     * 关注用户
     */
    @Transactional
    public void follow(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw new BusinessException("不能关注自己");
        }
        
        // 检查目标用户是否存在
        User targetUser = userMapper.selectById(followingId);
        if (targetUser == null) {
            throw new BusinessException("用户不存在");
        }
        
        // 检查是否已关注
        Long count = followMapper.selectCount(
            new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, followerId)
                .eq(Follow::getFollowingId, followingId)
        );
        
        if (count > 0) {
            throw new BusinessException("已关注该用户");
        }
        
        // 创建关注记录
        Follow follow = new Follow();
        follow.setFollowerId(followerId);
        follow.setFollowingId(followingId);
        followMapper.insert(follow);
        
        // 更新关注者的关注数
        User follower = userMapper.selectById(followerId);
        follower.setFollowCount(follower.getFollowCount() + 1);
        userMapper.updateById(follower);
        
        // 更新被关注者的粉丝数
        targetUser.setFansCount(targetUser.getFansCount() + 1);
        userMapper.updateById(targetUser);
    }
    
    /**
     * 取消关注
     */
    @Transactional
    public void unfollow(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw new BusinessException("不能取消关注自己");
        }
        
        // 检查目标用户是否存在
        User targetUser = userMapper.selectById(followingId);
        if (targetUser == null) {
            throw new BusinessException("用户不存在");
        }
        
        // 检查是否已关注
        Follow follow = followMapper.selectOne(
            new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, followerId)
                .eq(Follow::getFollowingId, followingId)
        );
        
        if (follow == null) {
            throw new BusinessException("未关注该用户");
        }
        
        // 删除关注记录
        followMapper.deleteById(follow.getId());
        
        // 更新关注者的关注数
        User follower = userMapper.selectById(followerId);
        follower.setFollowCount(Math.max(0, follower.getFollowCount() - 1));
        userMapper.updateById(follower);
        
        // 更新被关注者的粉丝数
        targetUser.setFansCount(Math.max(0, targetUser.getFansCount() - 1));
        userMapper.updateById(targetUser);
    }
    
    /**
     * 获取关注状态
     */
    public FollowStatusResponse getFollowStatus(Long currentUserId, Long targetUserId) {
        // 当前用户是否关注了目标用户
        boolean isFollowing = followMapper.selectCount(
            new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, currentUserId)
                .eq(Follow::getFollowingId, targetUserId)
        ) > 0;
        
        // 目标用户是否关注了当前用户
        boolean isFollowed = followMapper.selectCount(
            new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, targetUserId)
                .eq(Follow::getFollowingId, currentUserId)
        ) > 0;
        
        return new FollowStatusResponse(isFollowing, isFollowed);
    }
    
    /**
     * 获取关注列表
     */
    public PageResult<FollowUserResponse> getFollowing(Long userId, Long currentUserId, int page, int limit) {
        Page<Follow> pageParam = new Page<>(page, limit);
        IPage<Follow> result = followMapper.selectPage(pageParam,
            new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, userId)
                .orderByDesc(Follow::getCreatedAt)
        );
        
        if (result.getRecords().isEmpty()) {
            return PageResult.empty(page, limit);
        }
        
        // 获取关注的用户ID列表
        List<Long> followingIds = result.getRecords().stream()
            .map(Follow::getFollowingId)
            .toList();
        
        // 获取关注时间映射
        Map<Long, Follow> followMap = result.getRecords().stream()
            .collect(Collectors.toMap(Follow::getFollowingId, f -> f));
        
        // 查询用户信息
        List<User> users = userMapper.selectBatchIds(followingIds);
        Map<Long, User> userMap = users.stream()
            .collect(Collectors.toMap(User::getId, u -> u));
        
        // 如果有当前用户，查询当前用户的关注状态
        Set<Long> currentUserFollowingSet = getCurrentUserFollowingSet(currentUserId);
        Set<Long> currentUserFollowerSet = getCurrentUserFollowerSet(currentUserId);
        
        // 构建响应
        List<FollowUserResponse> responses = followingIds.stream()
            .map(followingId -> {
                User user = userMap.get(followingId);
                if (user == null) {
                    return null;
                }
                Follow follow = followMap.get(followingId);
                return buildFollowUserResponse(user, follow, currentUserId,
                    currentUserFollowingSet, currentUserFollowerSet);
            })
            .filter(r -> r != null)
            .toList();
        
        return new PageResult<>(responses, page, limit, result.getTotal());
    }
    
    /**
     * 获取粉丝列表
     */
    public PageResult<FollowUserResponse> getFollowers(Long userId, Long currentUserId, int page, int limit) {
        Page<Follow> pageParam = new Page<>(page, limit);
        IPage<Follow> result = followMapper.selectPage(pageParam,
            new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowingId, userId)
                .orderByDesc(Follow::getCreatedAt)
        );
        
        if (result.getRecords().isEmpty()) {
            return PageResult.empty(page, limit);
        }
        
        // 获取粉丝的用户ID列表
        List<Long> followerIds = result.getRecords().stream()
            .map(Follow::getFollowerId)
            .toList();
        
        // 获取关注时间映射
        Map<Long, Follow> followMap = result.getRecords().stream()
            .collect(Collectors.toMap(Follow::getFollowerId, f -> f));
        
        // 查询用户信息
        List<User> users = userMapper.selectBatchIds(followerIds);
        Map<Long, User> userMap = users.stream()
            .collect(Collectors.toMap(User::getId, u -> u));
        
        // 如果有当前用户，查询当前用户的关注状态
        Set<Long> currentUserFollowingSet = getCurrentUserFollowingSet(currentUserId);
        Set<Long> currentUserFollowerSet = getCurrentUserFollowerSet(currentUserId);
        
        // 构建响应
        List<FollowUserResponse> responses = followerIds.stream()
            .map(followerId -> {
                User user = userMap.get(followerId);
                if (user == null) {
                    return null;
                }
                Follow follow = followMap.get(followerId);
                return buildFollowUserResponse(user, follow, currentUserId,
                    currentUserFollowingSet, currentUserFollowerSet);
            })
            .filter(r -> r != null)
            .toList();
        
        return new PageResult<>(responses, page, limit, result.getTotal());
    }
    
    /**
     * 获取互关列表
     */
    public PageResult<FollowUserResponse> getMutualFollows(Long userId, Long currentUserId, int page, int limit) {
        // 先获取用户关注的人
        List<Follow> followingList = followMapper.selectList(
            new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, userId)
        );
        
        if (followingList.isEmpty()) {
            return PageResult.empty(page, limit);
        }
        
        List<Long> followingIds = followingList.stream()
            .map(Follow::getFollowingId)
            .toList();
        
        // 从关注的人中查找也关注了用户的人（互关）
        Page<Follow> pageParam = new Page<>(page, limit);
        IPage<Follow> result = followMapper.selectPage(pageParam,
            new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowingId, userId)
                .in(Follow::getFollowerId, followingIds)
                .orderByDesc(Follow::getCreatedAt)
        );
        
        if (result.getRecords().isEmpty()) {
            return PageResult.empty(page, limit);
        }
        
        // 获取互关用户的ID列表
        List<Long> mutualIds = result.getRecords().stream()
            .map(Follow::getFollowerId)
            .toList();
        
        // 获取关注时间映射
        Map<Long, Follow> followMap = result.getRecords().stream()
            .collect(Collectors.toMap(Follow::getFollowerId, f -> f));
        
        // 查询用户信息
        List<User> users = userMapper.selectBatchIds(mutualIds);
        Map<Long, User> userMap = users.stream()
            .collect(Collectors.toMap(User::getId, u -> u));
        
        // 如果有当前用户，查询当前用户的关注状态
        Set<Long> currentUserFollowingSet = getCurrentUserFollowingSet(currentUserId);
        Set<Long> currentUserFollowerSet = getCurrentUserFollowerSet(currentUserId);
        
        // 构建响应
        List<FollowUserResponse> responses = mutualIds.stream()
            .map(mutualId -> {
                User user = userMap.get(mutualId);
                if (user == null) {
                    return null;
                }
                Follow follow = followMap.get(mutualId);
                FollowUserResponse response = buildFollowUserResponse(user, follow, currentUserId,
                    currentUserFollowingSet, currentUserFollowerSet);
                // 互关列表中这些用户一定是互关状态
                response.setIsMutual(true);
                return response;
            })
            .filter(r -> r != null)
            .toList();
        
        return new PageResult<>(responses, page, limit, result.getTotal());
    }
    
    // ============ 私有辅助方法 ============
    
    /**
     * 获取当前用户关注的用户ID集合
     */
    private Set<Long> getCurrentUserFollowingSet(Long currentUserId) {
        if (currentUserId == null) {
            return Set.of();
        }
        return followMapper.selectList(
            new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, currentUserId)
        ).stream()
            .map(Follow::getFollowingId)
            .collect(Collectors.toSet());
    }
    
    /**
     * 获取关注当前用户的用户ID集合
     */
    private Set<Long> getCurrentUserFollowerSet(Long currentUserId) {
        if (currentUserId == null) {
            return Set.of();
        }
        return followMapper.selectList(
            new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowingId, currentUserId)
        ).stream()
            .map(Follow::getFollowerId)
            .collect(Collectors.toSet());
    }
    
    /**
     * 构建关注用户响应
     */
    private FollowUserResponse buildFollowUserResponse(User user, Follow follow, Long currentUserId,
            Set<Long> currentUserFollowingSet, Set<Long> currentUserFollowerSet) {
        FollowUserResponse response = new FollowUserResponse();
        response.setId(user.getId());
        response.setUserId(user.getUserId());
        response.setNickname(user.getNickname());
        response.setAvatar(user.getAvatar());
        response.setBio(user.getBio());
        response.setVerified(user.getVerified());
        response.setFollowedAt(follow != null ? follow.getCreatedAt() : null);
        
        // 计算关注状态
        if (currentUserId != null && !currentUserId.equals(user.getId())) {
            boolean isFollowing = currentUserFollowingSet.contains(user.getId());
            boolean isFollowed = currentUserFollowerSet.contains(user.getId());
            boolean isMutual = isFollowing && isFollowed;
            
            response.setIsFollowing(isFollowing);
            response.setIsFollowed(isFollowed);
            response.setIsMutual(isMutual);
            
            if (isMutual) {
                response.setButtonType("mutual");
            } else if (isFollowing) {
                response.setButtonType("following");
            } else {
                response.setButtonType("follow");
            }
        }
        
        return response;
    }
}
