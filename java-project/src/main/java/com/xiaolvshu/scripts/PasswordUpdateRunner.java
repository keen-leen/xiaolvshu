package com.xiaolvshu.scripts;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaolvshu.entity.Admin;
import com.xiaolvshu.entity.User;
import com.xiaolvshu.mapper.AdminMapper;
import com.xiaolvshu.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 密码统一更新脚本
 * 将数据库中所有用户和管理员的密码更新为 PasswordEncoder（BCrypt）哈希后的 "123456"。
 *
 * 使用方式（二选一）：
 * 1. 配置：在 application.yml 中设置 script.password-update.enabled=true，然后启动应用
 * 2. 命令行：mvn spring-boot:run -Dspring-boot.run.arguments="--script.password-update.enabled=true"
 *    或：java -jar app.jar --script.password-update.enabled=true
 *
 * 执行完成后请将配置改回 false 或删除，避免重复执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "script.password-update.enabled", havingValue = "true")
public class PasswordUpdateRunner implements CommandLineRunner {

    private static final String PLAIN_PASSWORD = "123456";

    private final UserMapper userMapper;
    private final AdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        log.info("========================================");
        log.info("开始执行密码更新脚本");
        log.info("目标密码: {}", PLAIN_PASSWORD);
        log.info("========================================");

        String encoded = passwordEncoder.encode(PLAIN_PASSWORD);

        // 更新所有用户密码
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<>());
        int userCount = 0;
        for (User user : users) {
            user.setPassword(encoded);
            userMapper.updateById(user);
            userCount++;
        }
        log.info("已更新 {} 个用户密码", userCount);

        // 更新所有管理员密码
        List<Admin> admins = adminMapper.selectList(new LambdaQueryWrapper<>());
        int adminCount = 0;
        for (Admin admin : admins) {
            admin.setPassword(encoded);
            adminMapper.updateById(admin);
            adminCount++;
        }
        log.info("已更新 {} 个管理员密码", adminCount);

        log.info("========================================");
        log.info("密码更新脚本执行完成");
        log.info("========================================");

        // 执行完毕后退出应用，避免常驻
        System.exit(0);
    }
}
