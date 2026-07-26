package com.xiaolvshu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaolvshu.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 用户Mapper
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Update("UPDATE users SET like_count = GREATEST(0, like_count + #{delta}) WHERE id = #{id}")
    int adjustLikeCount(@Param("id") Long id, @Param("delta") int delta);

    /**
     * 原子调整用户主动关注数，最小值限制为 0，以兼容重复取消等边界情况。
     */
    @Update("UPDATE users SET follow_count = GREATEST(0, follow_count + #{delta}) WHERE id = #{id}")
    int adjustFollowCount(@Param("id") Long id, @Param("delta") int delta);

    /**
     * 原子调整用户粉丝数，避免并发关注时基于旧 User 对象覆盖其他更新。
     */
    @Update("UPDATE users SET fans_count = GREATEST(0, fans_count + #{delta}) WHERE id = #{id}")
    int adjustFansCount(@Param("id") Long id, @Param("delta") int delta);
}
