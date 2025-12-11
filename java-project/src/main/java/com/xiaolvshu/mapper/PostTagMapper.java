package com.xiaolvshu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaolvshu.entity.PostTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 笔记标签关联Mapper
 */
@Mapper
public interface PostTagMapper extends BaseMapper<PostTag> {
}
