package com.xiaolvshu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 上传配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {
    
    private ImageUpload image = new ImageUpload();
    private VideoUpload video = new VideoUpload();
    
    @Data
    public static class ImageUpload {
        private String strategy = "imagehost"; // local, imagehost, r2
        private long maxSize = 10485760L; // 10MB
        private String allowedTypes = "image/jpeg,image/png,image/gif,image/webp";
        private LocalConfig local = new LocalConfig();
        private ImageHostConfig imagehost = new ImageHostConfig();
        private R2Config r2 = new R2Config();
    }
    
    @Data
    public static class VideoUpload {
        private String strategy = "local"; // local, r2
        private long maxSize = 104857600L; // 100MB
        private String allowedTypes = "video/mp4,video/avi,video/mov,video/wmv,video/flv,video/webm";
        private LocalConfig local = new LocalConfig();
        private R2Config r2 = new R2Config();
    }
    
    @Data
    public static class LocalConfig {
        private String uploadDir = "uploads/images";
        private String baseUrl = "http://localhost:3001";
    }
    
    @Data
    public static class ImageHostConfig {
        private String apiUrl = "https://api.xinyew.cn/api/jdtc";
        private int timeout = 60000;
    }
    
    @Data
    public static class R2Config {
        private String accountId;
        private String accessKeyId;
        private String secretAccessKey;
        private String bucketName;
        private String endpoint;
        private String publicUrl;
        private String region = "auto";
    }
}
