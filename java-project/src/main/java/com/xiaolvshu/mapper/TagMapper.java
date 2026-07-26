package com.xiaolvshu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaolvshu.entity.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 标签Mapper
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    /**
     * 原子调整标签使用次数，保证并发发布或删除笔记时计数不会丢失更新。
     */
    @Update("UPDATE tags SET use_count = GREATEST(0, use_count + #{delta}) WHERE id = #{id}")
    int adjustUseCount(@Param("id") Integer id, @Param("delta") int delta);
}
