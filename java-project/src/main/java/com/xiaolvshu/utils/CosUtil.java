package com.xiaolvshu.utils;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.DeleteObjectRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.region.Region;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 腾讯云 COS 对象存储工具类
 */
@Slf4j
@Component
public class CosUtil {
    
    @Value("${cos.secret-id:}")
    private String secretId;

    @Value("${cos.secret-key:}")
    private String secretKey;

    @Value("${cos.region:ap-guangzhou}")
    private String region;

    @Value("${cos.bucket-name:}")
    private String bucketName;

    @Value("${cos.base-url:}")
    private String baseUrl;

    // 支持的图片类型
    private static final List<String> IMAGE_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"
    );

    // 支持的视频类型
    private static final List<String> VIDEO_TYPES = Arrays.asList(
            "video/mp4", "video/avi", "video/mov", "video/wmv", "video/flv", "video/webm"
    );

    // 复用 COSClient，避免每次请求都创建
    private COSClient cosClient;

    @PostConstruct
    public void init() {
        if (isConfigured()) {
            this.cosClient = createCosClient();
            log.info("COS 客户端初始化成功，区域: {}, 桶: {}", region, bucketName);
        } else {
            log.warn("COS 配置不完整，COS 功能不可用");
        }
    }

    @PreDestroy
    public void destroy() {
        if (cosClient != null) {
            cosClient.shutdown();
            log.info("COS 客户端已关闭");
        }
    }

    /**
     * 检查 COS 是否已配置
     */
    public boolean isConfigured() {
        return secretId != null && !secretId.isBlank()
                && secretKey != null && !secretKey.isBlank()
                && bucketName != null && !bucketName.isBlank();
    }

    /**
     * 上传图片文件
     *
     * @param file 图片文件
     * @return 文件访问 URL
     */
    public String uploadImage(MultipartFile file) throws IOException {
        validateFile(file, IMAGE_TYPES, 10 * 1024 * 1024); // 10MB 限制
        return uploadFile(file, "images");
    }

    /**
     * 上传视频文件
     *
     * @param file 视频文件
     * @return 文件访问 URL
     */
    public String uploadVideo(MultipartFile file) throws IOException {
        validateFile(file, VIDEO_TYPES, 100 * 1024 * 1024); // 100MB 限制
        return uploadFile(file, "videos");
    }

    /**
     * 上传任意文件
     *
     * @param file   文件
     * @param folder 存储目录（如 images、videos）
     * @return 文件访问 URL
     */
    public String uploadFile(MultipartFile file, String folder) throws IOException {
        ensureConfigured();

        String key = generateKey(file.getOriginalFilename(), folder);

        try (InputStream inputStream = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());

            PutObjectRequest putRequest = new PutObjectRequest(bucketName, key, inputStream, metadata);
            PutObjectResult result = cosClient.putObject(putRequest);

            String url = buildAccessUrl(key);
            log.info("文件上传成功 - Key: {}, ETag: {}, URL: {}", key, result.getETag(), url);
            return url;
        } catch (CosClientException e) {
            log.error("文件上传失败 - Key: {}, 错误: {}", key, e.getMessage());
            throw new IOException("文件上传到 COS 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 上传字节数组
     *
     * @param bytes       文件字节
     * @param filename    原始文件名
     * @param contentType MIME 类型
     * @param folder      存储目录
     * @return 文件访问 URL
     */
    public String uploadBytes(byte[] bytes, String filename, String contentType, String folder) throws IOException {
        ensureConfigured();

        String key = generateKey(filename, folder);

        try (InputStream inputStream = new java.io.ByteArrayInputStream(bytes)) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            metadata.setContentType(contentType);

            PutObjectRequest putRequest = new PutObjectRequest(bucketName, key, inputStream, metadata);
            PutObjectResult result = cosClient.putObject(putRequest);

            String url = buildAccessUrl(key);
            log.info("字节数据上传成功 - Key: {}, ETag: {}", key, result.getETag());
            return url;
        } catch (CosClientException e) {
            log.error("字节数据上传失败 - Key: {}, 错误: {}", key, e.getMessage());
            throw new IOException("文件上传到 COS 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除文件
     *
     * @param fileUrl 文件 URL 或 Key
     * @return 是否删除成功
     */
    public boolean deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return false;
        }
        ensureConfigured();

        try {
            String key = extractKeyFromUrl(fileUrl);
            cosClient.deleteObject(new DeleteObjectRequest(bucketName, key));
            log.info("文件删除成功 - Key: {}", key);
            return true;
        } catch (CosClientException e) {
            log.error("文件删除失败 - URL: {}, 错误: {}", fileUrl, e.getMessage());
            return false;
        }
    }

    /**
     * 批量删除文件
     *
     * @param fileUrls 文件 URL 列表
     */
    public void deleteFiles(List<String> fileUrls) {
        if (fileUrls == null || fileUrls.isEmpty()) {
            return;
        }
        for (String url : fileUrls) {
            deleteFile(url);
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 创建 COS 客户端
     */
    private COSClient createCosClient() {
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        Region cosRegion = new Region(this.region);
        ClientConfig clientConfig = new ClientConfig(cosRegion);
        clientConfig.setHttpProtocol(HttpProtocol.https);
        // 设置超时时间
        clientConfig.setConnectionTimeout(30 * 1000);
        clientConfig.setSocketTimeout(60 * 1000);
        return new COSClient(cred, clientConfig);
    }

    /**
     * 确保 COS 已配置
     */
    private void ensureConfigured() {
        if (cosClient == null) {
            throw new IllegalStateException("COS 未配置或配置不完整，请检查 cos.secret-id、cos.secret-key、cos.bucket-name");
        }
    }

    /**
     * 验证文件
     */
    private void validateFile(MultipartFile file, List<String> allowedTypes, long maxSize) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("文件大小超过限制: " + (maxSize / 1024 / 1024) + "MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !allowedTypes.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("不支持的文件类型: " + contentType);
        }
    }

    /**
     * 生成唯一的文件 Key
     * 格式: folder/yyyy/MM/dd/时间戳_随机数.扩展名
     */
    private String generateKey(String originalFilename, String folder) {
        String extension = getFileExtension(originalFilename);
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uniqueName = System.currentTimeMillis() + "_" + ThreadLocalRandom.current().nextInt(1000, 9999);
        return String.format("%s/%s/%s%s", folder, datePath, uniqueName, extension);
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.'));
    }

    /**
     * 构建文件访问 URL
     */
    private String buildAccessUrl(String key) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            // 使用自定义域名
            String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            return base + "/" + key;
        }
        // 使用默认 COS 域名
        return String.format("https://%s.cos.%s.myqcloud.com/%s", bucketName, region, key);
    }

    /**
     * 从 URL 中提取文件 Key
     */
    private String extractKeyFromUrl(String fileUrl) {
        // 如果已经是 key（不包含 http），直接返回
        if (!fileUrl.startsWith("http")) {
            return fileUrl;
        }
        // 从 URL 中提取 key
        if (baseUrl != null && !baseUrl.isBlank() && fileUrl.startsWith(baseUrl)) {
            return fileUrl.substring(baseUrl.length()).replaceFirst("^/", "");
        }
        // 默认域名格式: https://bucket.cos.region.myqcloud.com/key
        String defaultDomain = String.format("https://%s.cos.%s.myqcloud.com/", bucketName, region);
        if (fileUrl.startsWith(defaultDomain)) {
            return fileUrl.substring(defaultDomain.length());
        }
        // 尝试从最后一个 / 后提取
        int lastSlash = fileUrl.lastIndexOf('/');
        return lastSlash > 0 ? fileUrl.substring(lastSlash + 1) : fileUrl;
    }
}
