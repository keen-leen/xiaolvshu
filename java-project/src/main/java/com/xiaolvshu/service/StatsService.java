package com.xiaolvshu.service;

import com.xiaolvshu.dto.SystemStatsResponse;
import com.xiaolvshu.mapper.CommentMapper;
import com.xiaolvshu.mapper.LikeMapper;
import com.xiaolvshu.mapper.PostMapper;
import com.xiaolvshu.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 系统统计服务
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final LikeMapper likeMapper;

    /**
     * 获取系统统计信息
     *
     * @return 系统统计响应
     */
    public SystemStatsResponse getSystemStats() {
        Long userCount = userMapper.selectCount(null);
        Long postCount = postMapper.selectCount(null);
        Long commentCount = commentMapper.selectCount(null);
        Long likeCount = likeMapper.selectCount(null);

        return new SystemStatsResponse(userCount, postCount, commentCount, likeCount);
    }
}
