package com.xiaolvshu.service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xiaolvshu.entity.PostVideo;
import com.xiaolvshu.mapper.PostVideoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 笔记视频服务
 */
@Service
@RequiredArgsConstructor
public class PostVideoService extends ServiceImpl<PostVideoMapper, PostVideo> {
}
