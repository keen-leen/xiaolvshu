package com.xiaolvshu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaolvshu.entity.Post;
import org.apache.ibatis.annotations.Mapper;

/**
 * 帖子Mapper
 */
@Mapper
public interface PostMapper extends BaseMapper<Post> {
}
