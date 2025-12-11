package com.xiaolvshu.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaolvshu.entity.UserSession;
import com.xiaolvshu.mapper.UserSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户会话服务
 */
@Service
@RequiredArgsConstructor
public class UserSessionService extends ServiceImpl<UserSessionMapper, UserSession> {
}
