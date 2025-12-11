package com.xiaolvshu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaolvshu.entity.Like;
import org.apache.ibatis.annotations.Mapper;

/**
 * 点赞Mapper
 */
@Mapper
public interface LikeMapper extends BaseMapper<Like> {
}
