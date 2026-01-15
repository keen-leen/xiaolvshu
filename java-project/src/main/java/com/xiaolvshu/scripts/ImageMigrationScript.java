package com.xiaolvshu.scripts;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaolvshu.entity.PostImage;
import com.xiaolvshu.entity.User;
import com.xiaolvshu.mapper.PostImageMapper;
import com.xiaolvshu.mapper.UserMapper;
import com.xiaolvshu.utils.CosUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 图片迁移脚本
 * 将数据库中的外部图片链接转存到 COS，并更新数据库记录
 * 
 * 使用方式：
 * 1. 在 application.yml 中设置 script.image-migration.enabled=true
 * 2. 启动应用程序，脚本会自动执行
 * 3. 执行完成后，将配置改回 false 或删除
 * 
 * 也可以通过命令行参数启动：
 * java -jar app.jar --script.image-migration.enabled=true
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "script.image-migration.enabled", havingValue = "true")
public class ImageMigrationScript implements CommandLineRunner {

    private final PostImageMapper postImageMapper;
    private final UserMapper userMapper;
    private final CosUtil cosUtil;

    // 统计信息
    private final AtomicInteger totalCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger skipCount = new AtomicInteger(0);
    private final AtomicInteger failCount = new AtomicInteger(0);

    // 配置
    private static final int BATCH_SIZE = 100;           // 每批处理数量
    private static final int CONNECTION_TIMEOUT = 10000; // 连接超时 10秒
    private static final int READ_TIMEOUT = 30000;       // 读取超时 30秒
    private static final int MAX_RETRIES = 3;            // 最大重试次数

    @Override
    public void run(String... args) throws Exception {
        log.info("========================================");
        log.info("开始执行图片迁移脚本");
        log.info("========================================");

        if (!cosUtil.isConfigured()) {
            log.error("COS 未配置，无法执行迁移脚本");
            return;
        }

        long startTime = System.currentTimeMillis();

        try {
            // 1. 迁移笔记图片
            migratePostImages();

            // 2. 迁移用户头像
            // migrateUserAvatars();

            long duration = (System.currentTimeMillis() - startTime) / 1000;
            log.info("========================================");
            log.info("图片迁移完成！耗时: {}秒", duration);
            log.info("总计: {}, 成功: {}, 跳过: {}, 失败: {}",
                    totalCount.get(), successCount.get(), skipCount.get(), failCount.get());
            log.info("========================================");

        } catch (Exception e) {
            log.error("图片迁移脚本执行失败", e);
            throw e;
        }
    }

    /**
     * 迁移笔记图片
     */
    private void migratePostImages() {
        log.info("开始迁移笔记图片...");

        // 分批查询所有笔记图片
        int offset = 0;
        List<PostImage> images;

        do {
            images = postImageMapper.selectList(
                    new LambdaQueryWrapper<PostImage>()
                            .last("LIMIT " + offset + ", " + BATCH_SIZE));

            for (PostImage image : images) {
                totalCount.incrementAndGet();
                processPostImage(image);
            }

            offset += BATCH_SIZE;
            log.info("已处理笔记图片: {} 条", offset);

        } while (images.size() == BATCH_SIZE);

        log.info("笔记图片迁移完成，共处理 {} 条", totalCount.get());
    }

    /**
     * 处理单个笔记图片
     */
    private void processPostImage(PostImage image) {
        String imageUrl = image.getImageUrl();

        // 检查是否需要迁移
        if (!needsMigration(imageUrl)) {
            skipCount.incrementAndGet();
            log.debug("跳过已迁移图片: id={}, url={}", image.getId(), imageUrl);
            return;
        }

        try {
            // 下载图片
            byte[] imageBytes = downloadImage(imageUrl);
            if (imageBytes == null || imageBytes.length == 0) {
                failCount.incrementAndGet();
                log.warn("下载图片失败（空内容）: id={}, url={}", image.getId(), imageUrl);
                return;
            }

            // 获取文件扩展名和 MIME 类型
            String filename = extractFilename(imageUrl);
            String contentType = guessContentType(filename);

            // 上传到 COS
            String newUrl = cosUtil.uploadBytes(imageBytes, filename, contentType, "images");

            // 更新数据库
            image.setImageUrl(newUrl);
            postImageMapper.updateById(image);

            successCount.incrementAndGet();
            log.info("迁移成功: id={}, 原URL={}, 新URL={}", image.getId(), imageUrl, newUrl);

        } catch (Exception e) {
            failCount.incrementAndGet();
            log.error("迁移失败: id={}, url={}, 错误: {}", image.getId(), imageUrl, e.getMessage());
        }
    }

    /**
     * 迁移用户头像
     */
    private void migrateUserAvatars() {
        log.info("开始迁移用户头像...");

        int offset = 0;
        List<User> users;
        int avatarCount = 0;

        do {
            users = userMapper.selectList(
                    new LambdaQueryWrapper<User>()
                            .isNotNull(User::getAvatar)
                            .ne(User::getAvatar, "")
                            .last("LIMIT " + offset + ", " + BATCH_SIZE));

            for (User user : users) {
                totalCount.incrementAndGet();
                if (processUserAvatar(user)) {
                    avatarCount++;
                }
            }

            offset += BATCH_SIZE;

        } while (users.size() == BATCH_SIZE);

        log.info("用户头像迁移完成，共迁移 {} 个头像", avatarCount);
    }

    /**
     * 处理单个用户头像
     */
    private boolean processUserAvatar(User user) {
        String avatarUrl = user.getAvatar();

        // 检查是否需要迁移
        if (!needsMigration(avatarUrl)) {
            skipCount.incrementAndGet();
            return false;
        }

        try {
            // 下载头像
            byte[] imageBytes = downloadImage(avatarUrl);
            if (imageBytes == null || imageBytes.length == 0) {
                failCount.incrementAndGet();
                log.warn("下载头像失败: userId={}, url={}", user.getId(), avatarUrl);
                return false;
            }

            // 获取文件扩展名和 MIME 类型
            String filename = "avatar_" + user.getId() + getExtension(avatarUrl);
            String contentType = guessContentType(filename);

            // 上传到 COS（头像放到 avatars 目录）
            String newUrl = cosUtil.uploadBytes(imageBytes, filename, contentType, "avatars");

            // 更新数据库
            user.setAvatar(newUrl);
            userMapper.updateById(user);

            successCount.incrementAndGet();
            log.info("头像迁移成功: userId={}, 原URL={}, 新URL={}", user.getId(), avatarUrl, newUrl);
            return true;

        } catch (Exception e) {
            failCount.incrementAndGet();
            log.error("头像迁移失败: userId={}, url={}, 错误: {}", user.getId(), avatarUrl, e.getMessage());
            return false;
        }
    }

    /**
     * 判断图片是否需要迁移
     * 如果图片已经在 COS 上，则跳过
     */
    private boolean needsMigration(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        // 检查是否已经是 COS 链接（包含 myqcloud.com 或自定义域名）
        return !url.contains("myqcloud.com") && !url.contains("cos.");
    }

    /**
     * 下载图片，带重试机制
     */
    private byte[] downloadImage(String imageUrl) {
        for (int retry = 0; retry < MAX_RETRIES; retry++) {
            try {
                return doDownload(imageUrl);
            } catch (Exception e) {
                log.warn("下载失败，重试 {}/{}: {}", retry + 1, MAX_RETRIES, e.getMessage());
                if (retry < MAX_RETRIES - 1) {
                    try {
                        Thread.sleep(1000 * (retry + 1)); // 递增等待时间
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 执行下载
     */
    private byte[] doDownload(String imageUrl) throws Exception {
        URL url = new URL(imageUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("User-Agent", 
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setInstanceFollowRedirects(true);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("HTTP 响应码: " + responseCode);
            }

            try (InputStream inputStream = connection.getInputStream();
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                return outputStream.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    /**
     * 从 URL 中提取文件名
     */
    private String extractFilename(String url) {
        try {
            String path = new URL(url).getPath();
            int lastSlash = path.lastIndexOf('/');
            String filename = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
            
            // 移除查询参数
            int queryIndex = filename.indexOf('?');
            if (queryIndex > 0) {
                filename = filename.substring(0, queryIndex);
            }
            
            // 如果文件名为空或没有扩展名，生成一个默认的
            if (filename.isBlank() || !filename.contains(".")) {
                filename = "image_" + System.currentTimeMillis() + ".jpg";
            }
            
            return filename;
        } catch (Exception e) {
            return "image_" + System.currentTimeMillis() + ".jpg";
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getExtension(String url) {
        String filename = extractFilename(url);
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(dotIndex) : ".jpg";
    }

    /**
     * 根据文件名猜测 MIME 类型
     */
    private String guessContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        } else if (lower.endsWith(".gif")) {
            return "image/gif";
        } else if (lower.endsWith(".webp")) {
            return "image/webp";
        } else if (lower.endsWith(".bmp")) {
            return "image/bmp";
        } else {
            return "image/jpeg";
        }
    }
}
