package com.xiaolvshu.service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xiaolvshu.entity.PostImage;
import com.xiaolvshu.mapper.PostImageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 笔记图片服务
 */
@Service
@RequiredArgsConstructor
public class PostImageService extends ServiceImpl<PostImageMapper, PostImage> {
}
