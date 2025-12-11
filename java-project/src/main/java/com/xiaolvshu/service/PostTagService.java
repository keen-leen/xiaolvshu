package com.xiaolvshu.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaolvshu.entity.PostTag;
import com.xiaolvshu.mapper.PostTagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 笔记标签关联服务
 */
@Service
@RequiredArgsConstructor
public class PostTagService extends ServiceImpl<PostTagMapper, PostTag> {
}
