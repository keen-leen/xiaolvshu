package com.xiaolvshu.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
    
    /**
     * 增加标签使用次数
     */
    public void incrementUseCount(Integer tagId) {
        Tag tag = this.getById(tagId);
        if (tag != null) {
            tag.setUseCount(tag.getUseCount() != null ? tag.getUseCount() + 1 : 1);
            this.updateById(tag);
        }
    }
    
    /**
     * 减少标签使用次数
     */
    public void decrementUseCount(Integer tagId) {
        Tag tag = this.getById(tagId);
        if (tag != null) {
            tag.setUseCount(tag.getUseCount() != null && tag.getUseCount() > 0 ? tag.getUseCount() - 1 : 0);
            this.updateById(tag);
        }
    }
}
