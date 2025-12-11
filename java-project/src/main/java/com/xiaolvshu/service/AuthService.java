package com.xiaolvshu.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaolvshu.utils.JwtTokenUtil;
import com.xiaolvshu.context.UserContext;
import com.xiaolvshu.dto.AuthResponse;
import com.xiaolvshu.dto.LoginRequest;
import com.xiaolvshu.dto.RegisterRequest;
import com.xiaolvshu.dto.UserDTO;
import com.xiaolvshu.entity.User;
import com.xiaolvshu.entity.UserSession;
import com.xiaolvshu.exception.BusinessException;
import com.xiaolvshu.mapper.UserMapper;
import com.xiaolvshu.mapper.UserSessionMapper;
import com.xiaolvshu.utils.PasswordUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final UserMapper userMapper;
    private final UserSessionMapper userSessionMapper;
    private final JwtTokenUtil jwtTokenUtil;
    
    /**
     * 用户注册
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // 检查用户名是否已存在
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUserId, request.getUsername()));
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }
        
        // 创建用户
        User user = new User();
        user.setUserId("sl_" + RandomUtil.randomString(8));
        // 使用 SHA-256 哈希密码（与 Express 后端保持一致）
        user.setPassword(PasswordUtil.sha256(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        
        userMapper.insert(user);
        
        // 生成Token
        String accessToken = jwtTokenUtil.generateAccessToken(user.getId(), user.getUserId());
        String refreshToken = jwtTokenUtil.generateRefreshToken(user.getId(), user.getUserId());
        
        // 返回结果
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        AuthResponse.TokensDTO tokens = new AuthResponse.TokensDTO(
                accessToken, 
                refreshToken, 
                jwtTokenUtil.getExpiresInSeconds()
        );
        return new AuthResponse(userDTO, tokens);
    }
    
    /**
     * 用户登录
     */
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        // 查找用户
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUserId, request.getUserId()));
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getIsActive() == 0) {
            throw new BusinessException("账户已被禁用");
        }
        
        // 验证密码（SHA-256 哈希比较）
        if (!PasswordUtil.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("密码错误");
        }
        
        // 生成Token
        String accessToken = jwtTokenUtil.generateAccessToken(user.getId(), user.getUserId());
        String refreshToken = jwtTokenUtil.generateRefreshToken(user.getId(), user.getUserId());
        
        // 获取 User-Agent
        String userAgent = httpRequest.getHeader("User-Agent");
        if (userAgent == null) {
            userAgent = "";
        }
        
        // 清除旧会话
        UserSession updateSession = new UserSession();
        updateSession.setIsActive(0);
        userSessionMapper.update(updateSession, new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getUserId, user.getId()));
        
        // 保存新会话
        UserSession session = new UserSession();
        session.setUserId(user.getId());
        session.setToken(accessToken);
        session.setRefreshToken(refreshToken);
        session.setExpiresAt(LocalDateTime.now().plusDays(7));
        session.setUserAgent(userAgent);
        session.setIsActive(1);
        userSessionMapper.insert(session);
        
        log.info("用户登录成功 - 用户ID: {}, 小旅书号: {}", user.getId(), user.getUserId());
        
        // 返回结果
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        AuthResponse.TokensDTO tokens = new AuthResponse.TokensDTO(
                accessToken, 
                refreshToken, 
                jwtTokenUtil.getExpiresInSeconds()
        );
        return new AuthResponse(userDTO, tokens);
    }
    
    /**
     * 获取当前用户信息
     */
    public UserDTO getCurrentUser() {
        Long currentUserId = UserContext.getUserId();
        User user = userMapper.selectById(currentUserId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 处理兴趣字段
        if (user.getInterests() != null) {
            List<String> interests = JSONUtil.toList(user.getInterests(), String.class);
            userDTO.setInterests(interests);
        }
        return userDTO;
    }
}
