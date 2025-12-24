package com.xiaolvshu.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaolvshu.utils.IpLocationUtil;
import com.xiaolvshu.utils.JwtTokenUtil;
import com.xiaolvshu.context.UserContext;
import com.xiaolvshu.dto.AdminAuthResponse;
import com.xiaolvshu.dto.AdminDTO;
import com.xiaolvshu.dto.AdminLoginRequest;
import com.xiaolvshu.dto.AuthResponse;
import com.xiaolvshu.dto.LoginRequest;
import com.xiaolvshu.dto.RegisterRequest;
import com.xiaolvshu.dto.UserDTO;
import com.xiaolvshu.entity.Admin;
import com.xiaolvshu.entity.User;
import com.xiaolvshu.entity.UserSession;
import com.xiaolvshu.exception.BusinessException;
import com.xiaolvshu.mapper.AdminMapper;
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
public class AuthService{
    
    private final UserMapper userMapper;
    private final UserSessionMapper userSessionMapper;
    private final AdminMapper adminMapper;
    private final JwtTokenUtil jwtTokenUtil;
    private final CaptchaService captchaService;
    
    /**
     * 用户注册
     */
    @Transactional
    public AuthResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        // 1. 验证验证码
        if (!captchaService.validateCaptcha(request.getCaptchaId(), request.getCaptchaText())) {
             throw new BusinessException("验证码错误或已过期");
        }

        // 2. 验证 user_id 格式
        if (!request.getUserId().matches("^[a-zA-Z0-9_]+$")) {
            throw new BusinessException("小旅书号只能包含字母、数字和下划线");
        }

        // 3. 检查用户ID是否已存在
        boolean exists = userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getUserId, request.getUserId()));
        if (exists) {
            throw new BusinessException("用户ID已存在");
        }
        
        // 4. 创建用户
        User user = new User();
        user.setUserId(request.getUserId());
        user.setNickname(request.getNickname());
        // 使用 SHA-256 哈希密码
        user.setPassword(PasswordUtil.sha256(request.getPassword()));
        user.setAvatar("https://img20.360buyimg.com/openfeedback/jfs/t1/349561/26/2288/51193/68c324e1F0847c3c5/21f0e026204657da.png");
        user.setBio(request.getBio());
        
        String ip = IpLocationUtil.getClientIp(httpRequest);
        user.setLocation(IpLocationUtil.getIpLocation(ip));
        
        userMapper.insert(user);
        
        // 5. 生成Token
        String accessToken = jwtTokenUtil.generateAccessToken(user.getId(), user.getUserId());
        String refreshToken = jwtTokenUtil.generateRefreshToken(user.getId(), user.getUserId());
        
        // 6. 保存会话
        UserSession session = new UserSession();
        session.setUserId(user.getId());
        session.setToken(accessToken);
        session.setRefreshToken(refreshToken);
        session.setExpiresAt(LocalDateTime.now().plusDays(7));
        session.setUserAgent(httpRequest.getHeader("User-Agent"));
        session.setIsActive(1);
        userSessionMapper.insert(session);
        
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
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUserId, request.getUserId()));
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

        // 更新用户IP位置
        String ip = IpLocationUtil.getClientIp(httpRequest);
        String location = IpLocationUtil.getIpLocation(ip);
        if (!location.equals(user.getLocation())) {
            user.setLocation(location);
            userMapper.updateById(user);
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
        userSessionMapper.update(updateSession, new LambdaQueryWrapper<UserSession>().eq(UserSession::getUserId, user.getId()));
        
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
        AuthResponse.TokensDTO tokens = new AuthResponse.TokensDTO(accessToken, refreshToken, jwtTokenUtil.getExpiresInSeconds()
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
    
    /**
     * 刷新令牌
     */
    @Transactional
    public AuthResponse.TokensDTO refreshToken(String refreshToken, HttpServletRequest httpRequest) {
        // 验证刷新令牌
        Long userId;
        String userIdStr;
        try {
            userId = jwtTokenUtil.getUserIdFromToken(refreshToken);
            userIdStr = jwtTokenUtil.getUsernameFromToken(refreshToken);
        } catch (Exception e) {
            throw new BusinessException("刷新令牌无效");
        }
        
        // 检查会话是否有效
        UserSession session = userSessionMapper.selectOne(
            new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getUserId, userId)
                .eq(UserSession::getRefreshToken, refreshToken)
                .eq(UserSession::getIsActive, 1)
                .gt(UserSession::getExpiresAt, LocalDateTime.now())
        );
        
        if (session == null) {
            throw new BusinessException("刷新令牌无效或已过期");
        }
        
        // 生成新的令牌
        String newAccessToken = jwtTokenUtil.generateAccessToken(userId, userIdStr);
        String newRefreshToken = jwtTokenUtil.generateRefreshToken(userId, userIdStr);
        
        // 获取用户IP和User-Agent
        String ip = IpLocationUtil.getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        if (userAgent == null) {
            userAgent = "";
        }
        
        // 获取IP地理位置并更新用户location
        String ipLocation = IpLocationUtil.getIpLocation(ip);
        User user = userMapper.selectById(userId);
        if (user != null && !ipLocation.equals(user.getLocation())) {
            user.setLocation(ipLocation);
            userMapper.updateById(user);
        }
        
        // 更新会话
        session.setToken(newAccessToken);
        session.setRefreshToken(newRefreshToken);
        session.setExpiresAt(LocalDateTime.now().plusDays(7));
        session.setUserAgent(userAgent);
        userSessionMapper.updateById(session);
        
        log.info("令牌刷新成功 - 用户ID: {}", userId);
        
        return new AuthResponse.TokensDTO(
            newAccessToken, 
            newRefreshToken, 
            jwtTokenUtil.getExpiresInSeconds()
        );
    }
    
    /**
     * 退出登录
     */
    @Transactional
    public void logout(String token) {
        Long userId = UserContext.getUserId();
        
        // 将当前会话设为无效
        UserSession updateSession = new UserSession();
        updateSession.setIsActive(0);
        userSessionMapper.update(updateSession, new LambdaQueryWrapper<UserSession>().eq(UserSession::getUserId, userId).eq(UserSession::getToken, token)
        );
        
        log.info("用户退出成功 - 用户ID: {}", userId);
    }
    
    /**
     * 管理员登录
     */
    @Transactional
    public AdminAuthResponse adminLogin(AdminLoginRequest request) {
        // 查找管理员
        Admin admin = adminMapper.selectOne(
            new LambdaQueryWrapper<Admin>().eq(Admin::getUsername, request.getUsername())
        );
        
        if (admin == null) {
            throw new BusinessException("管理员账号不存在");
        }
        
        // 验证密码（SHA-256 哈希比较）
        if (!PasswordUtil.matches(request.getPassword(), admin.getPassword())) {
            throw new BusinessException("密码错误");
        }
        
        // 生成JWT令牌
        String accessToken = jwtTokenUtil.generateAccessToken(admin.getId(), admin.getUsername());
        String refreshToken = jwtTokenUtil.generateRefreshToken(admin.getId(), admin.getUsername());
        
        log.info("管理员登录成功 - 管理员ID: {}, 用户名: {}", admin.getId(), admin.getUsername());
        
        // 返回结果
        AdminDTO adminDTO = BeanUtil.copyProperties(admin, AdminDTO.class);
        AdminAuthResponse.TokensDTO tokens = new AdminAuthResponse.TokensDTO(
            accessToken,
            refreshToken,
            jwtTokenUtil.getExpiresInSeconds()
        );
        return new AdminAuthResponse(adminDTO, tokens);
    }
    
    /**
     * 获取当前管理员信息
     */
    public AdminDTO getCurrentAdmin() {
        Long adminId = UserContext.getUserId();
        Admin admin = adminMapper.selectById(adminId);
        if (admin == null) {
            throw new BusinessException("管理员不存在");
        }
        return BeanUtil.copyProperties(admin, AdminDTO.class);
    }
}

