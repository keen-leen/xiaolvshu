package com.xiaolvshu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaolvshu.context.UserContext;
import com.xiaolvshu.dto.FollowStatusResponse;
import com.xiaolvshu.dto.FollowUserResponse;
import com.xiaolvshu.dto.PageRequest;
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

import java.util.ArrayList;
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
    public PageResult<FollowUserResponse> getFollowing(String username, PageRequest pageRequest) {
        User targetUser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUserId, username)); // 确保用户存在
        if (targetUser == null) {
            throw new BusinessException("用户不存在");             
        }
        Long userId = targetUser.getId();
        Page<Follow> pageParam = new Page<>(pageRequest.getPage(), pageRequest.getLimit());
        IPage<Follow> result = followMapper.selectPage(pageParam, new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, userId)
                .orderByDesc(Follow::getCreatedAt)
        );
        
        if (result.getRecords().isEmpty()) {
            return PageResult.empty(pageRequest.getPage(), pageRequest.getLimit());
        }
        
        // 获取关注的用户ID列表
        List<Long> followingIds = result.getRecords().stream().map(Follow::getFollowingId).toList();
        
        Map<Long, Follow> followMap = result.getRecords().stream().collect(Collectors.toMap(Follow::getFollowingId, f -> f));
        
        // 查询用户信息
        List<User> users = userMapper.selectBatchIds(followingIds);
        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
        List<FollowUserResponse> response = new ArrayList<>();
        Long currentUserId = UserContext.getUserId();
        // 如果当前用户存在，获取其与关注用户的详细关系
        Set<Long> currentUserFollowingSet = getCurrentUserFollowingSet(currentUserId);
        Set<Long> currentUserFollowerSet = getCurrentUserFollowerSet(currentUserId);
        response = followingIds.stream().map(followingId -> {
            User user = userMap.get(followingId);
            Follow follow = followMap.get(followingId);
            return buildFollowUserResponse(user, follow, currentUserId, currentUserFollowingSet, currentUserFollowerSet);
        }).toList();
        return new PageResult<>(response, pageRequest.getPage(), pageRequest.getLimit(), result.getTotal());
    }
    
    /**
     * 获取粉丝列表
     */
    public PageResult<FollowUserResponse> getFollowers(String username, PageRequest pageRequest) {
        User targetUser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUserId, username));
        if (targetUser == null) {
            throw new BusinessException("用户不存在");
        }
        Long userId = targetUser.getId();
        Page<Follow> pageParam = new Page<>(pageRequest.getPage(), pageRequest.getLimit());
        IPage<Follow> result = followMapper.selectPage(pageParam,
            new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowingId, userId)
                .orderByDesc(Follow::getCreatedAt)
        );
        
        if (result.getRecords().isEmpty()) {
            return PageResult.empty(pageRequest.getPage(), pageRequest.getLimit());
        }

        // 获取粉丝的用户ID列表
        List<Long> followerIds = result.getRecords().stream().map(Follow::getFollowerId).toList();
        Map<Long, Follow> followMap = result.getRecords().stream().collect(Collectors.toMap(Follow::getFollowerId, f -> f));
        
        // 查询用户信息
        List<User> users = userMapper.selectBatchIds(followerIds);
        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
        
        List<FollowUserResponse> response = new ArrayList<>();
        Long currentUserId = UserContext.getUserId();
        // 如果当前用户存在，获取其与关注用户的详细关系
        Set<Long> currentUserFollowingSet = getCurrentUserFollowingSet(currentUserId);
        Set<Long> currentUserFollowerSet = getCurrentUserFollowerSet(currentUserId);
        response = followerIds.stream().map(followingId -> {
            User user = userMap.get(followingId);
            Follow follow = followMap.get(followingId);
            return buildFollowUserResponse(user, follow, currentUserId, currentUserFollowingSet, currentUserFollowerSet);
        }).toList();
        
        return new PageResult<>(response, pageRequest.getPage(), pageRequest.getLimit(), result.getTotal());
    }
    
    /**
     * 获取互关列表
     */
    public PageResult<FollowUserResponse> getMutualFollows(String username, PageRequest pageRequest) {
        User targetUser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUserId, username));
        if (targetUser == null) {
            throw new BusinessException("用户不存在");
        }
        Long userId = targetUser.getId();
        // 先获取用户关注的人
        List<Follow> followingList = followMapper.selectList(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, userId));
        
        if (followingList.isEmpty()) {
            return PageResult.empty(pageRequest.getPage(), pageRequest.getLimit());
        }
        
        List<Long> followingIds = followingList.stream().map(Follow::getFollowingId).toList();
        
        // 从关注的人中查找也关注了用户的人（互关）
        Page<Follow> pageParam = new Page<>(pageRequest.getPage(), pageRequest.getLimit());
        IPage<Follow> result = followMapper.selectPage(pageParam, new LambdaQueryWrapper<Follow>().eq(Follow::getFollowingId, userId).in(Follow::getFollowerId, followingIds).orderByDesc(Follow::getCreatedAt));
        
        if (result.getRecords().isEmpty()) {
            return PageResult.empty(pageRequest.getPage(), pageRequest.getLimit());
        }
        
        // 获取互关用户的ID列表
        List<Long> mutualIds = result.getRecords().stream().map(Follow::getFollowerId).toList();
        
        Map<Long, Follow> followMap = result.getRecords().stream().collect(Collectors.toMap(Follow::getFollowerId, f -> f));
        
        // 查询用户信息
        List<User> users = userMapper.selectBatchIds(mutualIds);
        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
        
        List<FollowUserResponse> response = new ArrayList<>();
        Long currentUserId = UserContext.getUserId();
        // 如果当前用户存在，获取其与关注用户的详细关系
        Set<Long> currentUserFollowingSet = getCurrentUserFollowingSet(currentUserId);
        Set<Long> currentUserFollowerSet = getCurrentUserFollowerSet(currentUserId);
        response = mutualIds.stream().map(mutualId -> {
            User user = userMap.get(mutualId);
            Follow follow = followMap.get(mutualId);
            FollowUserResponse followUserResponse = buildFollowUserResponse(user, follow, currentUserId, currentUserFollowingSet, currentUserFollowerSet);
            followUserResponse.setIsMutual(true);
            return followUserResponse;
        }).toList();
        
        return new PageResult<>(response, pageRequest.getPage(), pageRequest.getLimit(), result.getTotal());
    }
    
    /**
     * 获取当前用户关注的用户ID集合
     */
    private Set<Long> getCurrentUserFollowingSet(Long currentUserId) {
        if (currentUserId == null) {
            return Set.of();
        }
        return followMapper.selectList(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, currentUserId)).stream().map(Follow::getFollowingId).collect(Collectors.toSet());
    }
    
    /**
     * 获取关注当前用户的用户ID集合
     */
    private Set<Long> getCurrentUserFollowerSet(Long currentUserId) {
        if (currentUserId == null) {
            return Set.of();
        }
        return followMapper.selectList(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowingId, currentUserId)).stream().map(Follow::getFollowerId).collect(Collectors.toSet());
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
        response.setLocation(user.getLocation());
        response.setFollowCount(user.getFollowCount());
        response.setFansCount(user.getFansCount());
        response.setLikeCount(user.getLikeCount());
        response.setPostCount(user.getPostCount());
        response.setVerified(user.getVerified());
        response.setCreatedAt(user.getCreatedAt());
        response.setFollowedAt(follow != null ? follow.getCreatedAt() : null);
        
        // 计算关注状态
        if (currentUserId != null) {
            boolean isFollowing = currentUserFollowingSet.contains(user.getId());
            boolean isFollowed = currentUserFollowerSet.contains(user.getId());
            boolean isMutual = isFollowing && isFollowed;
            
            response.setIsFollowing(isFollowing);
            response.setIsMutual(isMutual);
            
            if (currentUserId == user.getId()) {
                // 当前检查的用户就是用户自己（不显示状态）
                response.setButtonType("self");
            } else if (isMutual) {
                // 不是自己且互相关注
                response.setButtonType("mutual");
            } else if (isFollowing) {
                // 不是自己且不是互相关注，但已关注（显示取消关注）
                response.setButtonType("unfollow");
            } else if (isFollowed) {
                // 不是自己也没有互相关注，没有关注，但是被关注（显示回关）
                response.setButtonType("back");
            } else {
                // 不是自己且不是互相关注且未关注且未被关注（显示关注）
                response.setButtonType("follow");
            }
        } else {
            response.setIsFollowing(false);
            response.setIsMutual(false);
            response.setButtonType("follow");
        }
        
        return response;
    }
}
