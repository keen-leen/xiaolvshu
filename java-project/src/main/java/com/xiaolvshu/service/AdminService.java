package com.xiaolvshu.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaolvshu.entity.Admin;
import com.xiaolvshu.mapper.AdminMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 管理员服务
 */
@Service
@RequiredArgsConstructor
public class AdminService extends ServiceImpl<AdminMapper, Admin> {
}
