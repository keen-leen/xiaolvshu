package com.xiaolvshu.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaolvshu.entity.Collection;
import com.xiaolvshu.mapper.CollectionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 收藏服务
 */
@Service
@RequiredArgsConstructor
public class CollectionService extends ServiceImpl<CollectionMapper, Collection> {
}
