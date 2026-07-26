package com.xiaolvshu.service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xiaolvshu.entity.Tag;
import com.xiaolvshu.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 标签服务
 */
@Service
@RequiredArgsConstructor
public class TagService extends ServiceImpl<TagMapper, Tag> {

    private final TagMapper tagMapper;
    
    /**
     * 增加标签使用次数
     */
    public void incrementUseCount(Integer tagId) {
        tagMapper.adjustUseCount(tagId, 1);
    }
    
    /**
     * 减少标签使用次数
     */
    public void decrementUseCount(Integer tagId) {
        tagMapper.adjustUseCount(tagId, -1);
    }
}
