package com.xiaolvshu.service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xiaolvshu.entity.Audit;
import com.xiaolvshu.mapper.AuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 审核服务
 */
@Service
@RequiredArgsConstructor
public class AuditService extends ServiceImpl<AuditMapper, Audit> {
}
