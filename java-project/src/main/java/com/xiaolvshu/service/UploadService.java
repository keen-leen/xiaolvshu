package com.xiaolvshu.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.xiaolvshu.config.UploadProperties;
import com.xiaolvshu.dto.UploadResult;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * 上传服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadService {
    
    private final UploadProperties uploadProperties;
    
    /**
     * 上传图片（根据配置策略）
     * @return 上传后的URL
     */
    public UploadResult uploadImage(MultipartFile file) {
        String strategy = uploadProperties.getImage().getStrategy();
        
        return switch (strategy) {
            // case "local" -> saveImageToLocal(file);
            case "imagehost" -> uploadToImageHost(file);
            // case "r2" -> uploadImageToR2(file);
            default -> throw new RuntimeException("未知的图片上传策略");
        };
    }
    
    /**
     * 上传视频（根据配置策略）
     * @return 上传后的URL
     */
    public String uploadVideo(MultipartFile file) {
        String strategy = uploadProperties.getVideo().getStrategy();
        
        return switch (strategy) {
            case "local" -> saveVideoToLocal(file);
            case "r2" -> uploadVideoToR2(file);
            default -> throw new RuntimeException("未知的视频上传策略");
        };
    }
    
    /**
     * 保存图片到本地
     */
    public String saveImageToLocal(MultipartFile file) {
        try {
            byte[] fileBytes = file.getBytes();
            String originalFilename = file.getOriginalFilename();
            
            // 使用MD5作为文件名
            String md5 = DigestUtil.md5Hex(fileBytes);
            String ext = getFileExtension(originalFilename);
            String newFilename = md5 + ext;
            
            // 按日期分目录
            String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String uploadDir = uploadProperties.getImage().getLocal().getUploadDir();
            String relativePath = dateDir + "/" + newFilename;
            String fullPath = uploadDir + "/" + relativePath;
            
            // 创建目录并写入文件
            Path filePath = Paths.get(fullPath);
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, fileBytes);
            
            // 生成访问URL
            String baseUrl = uploadProperties.getImage().getLocal().getBaseUrl();
            String url = baseUrl + "/" + uploadDir + "/" + relativePath;
            
            log.info("图片保存到本地成功: {}", fullPath);
            return url;
        } catch (IOException e) {
            log.error("图片保存到本地失败: {}", e.getMessage());
            throw new RuntimeException("图片保存失败: " + e.getMessage());
        }
    }
    
    /**
     * 保存视频到本地
     */
    public String saveVideoToLocal(MultipartFile file) {
        try {
            byte[] fileBytes = file.getBytes();
            String originalFilename = file.getOriginalFilename();
            
            // 使用MD5作为文件名
            String md5 = DigestUtil.md5Hex(fileBytes);
            String ext = getFileExtension(originalFilename);
            String newFilename = md5 + ext;
            
            // 按日期分目录
            String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String uploadDir = uploadProperties.getVideo().getLocal().getUploadDir();
            String relativePath = dateDir + "/" + newFilename;
            String fullPath = uploadDir + "/" + relativePath;
            
            // 创建目录并写入文件
            Path filePath = Paths.get(fullPath);
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, fileBytes);
            
            // 生成访问URL
            String baseUrl = uploadProperties.getVideo().getLocal().getBaseUrl();
            String url = baseUrl + "/" + uploadDir + "/" + relativePath;
            
            log.info("视频保存到本地成功: {}", fullPath);
            return url;
        } catch (IOException e) {
            log.error("视频保存到本地失败: {}", e.getMessage());
            throw new RuntimeException("视频保存失败: " + e.getMessage());
        }
    }

    /**
     * 上传图床响应结构
     */
    @Data
    public class UploadResponse {
        private Integer errno;
        private String message;
        private FileData data;

        @Data
        public class FileData {
            private String url;
            private String filename;
        }
    }
    /**
     * 上传图片到第三方图床
     */
    public UploadResult uploadToImageHost(MultipartFile file) {
        try {
            UploadProperties.ImageHostConfig config = uploadProperties.getImage().getImagehost();
            
            HttpResponse response = HttpRequest.post(config.getApiUrl())
                    .timeout(config.getTimeout())
                    .form("file", file.getBytes(), file.getOriginalFilename())
                    .execute();
            
            if (!response.isOk()) {
                log.error("图床上传失败: HTTP {}", response.getStatus());
                throw new RuntimeException("图床上传失败");
            }
            
            String body = response.body();
            UploadResponse resp = JSONUtil.toBean(body, UploadResponse.class);
            UploadResult result = new UploadResult();
            // 根据图床API响应格式解析
            if (resp.getErrno() == 0) {
                result.setOriginalname(resp.getData().getFilename());
                result.setUrl(resp.getData().getUrl());
                result.setSize(file.getSize());
            }
            return result;
        } catch (IOException e) {
            log.error("图床上传异常: {}", e.getMessage());
            throw new RuntimeException("图床上传失败: " + e.getMessage());
        }
    }
    
    /**
     * 上传图片到Cloudflare R2
     */
    public String uploadImageToR2(MultipartFile file) {
        try {
            UploadProperties.R2Config config = uploadProperties.getImage().getR2();
            
            if (StrUtil.isBlank(config.getAccessKeyId()) || StrUtil.isBlank(config.getSecretAccessKey())) {
                throw new RuntimeException("R2配置不完整");
            }
            
            String originalFilename = file.getOriginalFilename();
            String ext = getFileExtension(originalFilename);
            String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String key = "images/" + dateDir + "/" + IdUtil.simpleUUID() + ext;
            
            S3Client s3Client = createS3Client(config);
            
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(key)
                    .contentType(file.getContentType())
                    .build();
            
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));
            
            String publicUrl = StrUtil.isNotBlank(config.getPublicUrl()) 
                    ? config.getPublicUrl() 
                    : config.getEndpoint();
            String url = publicUrl + "/" + key;
            
            log.info("R2上传图片成功: {}", url);
            return url;
        } catch (IOException e) {
            log.error("R2上传图片失败: {}", e.getMessage());
            throw new RuntimeException("R2上传失败: " + e.getMessage());
        }
    }
    
    /**
     * 上传视频到Cloudflare R2
     */
    public String uploadVideoToR2(MultipartFile file) {
        try {
            UploadProperties.R2Config config = uploadProperties.getVideo().getR2();
            
            if (StrUtil.isBlank(config.getAccessKeyId()) || StrUtil.isBlank(config.getSecretAccessKey())) {
                throw new RuntimeException("R2配置不完整");
            }
            
            String originalFilename = file.getOriginalFilename();
            String ext = getFileExtension(originalFilename);
            String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String key = "videos/" + dateDir + "/" + IdUtil.simpleUUID() + ext;
            
            S3Client s3Client = createS3Client(config);
            
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(key)
                    .contentType(file.getContentType())
                    .build();
            
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));
            
            String publicUrl = StrUtil.isNotBlank(config.getPublicUrl()) 
                    ? config.getPublicUrl() 
                    : config.getEndpoint();
            String url = publicUrl + "/" + key;
            
            log.info("R2上传视频成功: {}", url);
            return url;
        } catch (IOException e) {
            log.error("R2上传视频失败: {}", e.getMessage());
            throw new RuntimeException("R2上传失败: " + e.getMessage());
        }
    }
    
    private S3Client createS3Client(UploadProperties.R2Config config) {
        return S3Client.builder()
                .region(Region.of(config.getRegion()))
                .endpointOverride(URI.create(config.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.getAccessKeyId(), config.getSecretAccessKey())
                ))
                .build();
    }
    
    private String getFileExtension(String filename) {
        if (StrUtil.isBlank(filename)) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex >= 0 ? filename.substring(dotIndex) : "";
    }
    
    public boolean isValidImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        String contentType = file.getContentType();
        if (StrUtil.isBlank(contentType)) {
            return false;
        }
        List<String> allowedTypes = Arrays.asList(uploadProperties.getImage().getAllowedTypes().split(","));
        return allowedTypes.contains(contentType);
    }
    
    public boolean isValidVideoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        String contentType = file.getContentType();
        if (StrUtil.isBlank(contentType)) {
            return false;
        }
        List<String> allowedTypes = Arrays.asList(uploadProperties.getVideo().getAllowedTypes().split(","));
        return allowedTypes.contains(contentType);
    }
    
    public boolean checkImageFileSize(MultipartFile file) {
        return file.getSize() <= uploadProperties.getImage().getMaxSize();
    }
    
    public boolean checkVideoFileSize(MultipartFile file) {
        return file.getSize() <= uploadProperties.getVideo().getMaxSize();
    }
}
