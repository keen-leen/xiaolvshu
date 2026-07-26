package com.xiaolvshu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaolvshu.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 评论Mapper
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    @Update("UPDATE comments SET like_count = GREATEST(0, like_count + #{delta}) WHERE id = #{id}")
    int adjustLikeCount(@Param("id") Long id, @Param("delta") int delta);
}
