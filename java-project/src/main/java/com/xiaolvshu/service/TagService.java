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
}
