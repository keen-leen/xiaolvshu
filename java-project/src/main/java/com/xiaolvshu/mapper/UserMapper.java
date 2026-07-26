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
}
