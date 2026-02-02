package com.xiaolvshu.service;

import cn.hutool.core.util.StrUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 文件上传服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadService {

    @Value("${app.upload.image.max-size}")
    private Integer imageMaxSize;
    @Value("${app.upload.image.allowed-types}")
    private String imageAllowedTypes;
    @Value("${app.upload.video.max-size}")
    private Integer videoMaxSize;
    @Value("${app.upload.video.allowed-types}")
    private String videoAllowedTypes;

    public boolean isValidImageFile(MultipartFile file, Integer fileType) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        String contentType = file.getContentType();
        if (StrUtil.isBlank(contentType)) {
            return false;
        }
        List<String> allowedTypes = new ArrayList<>();
        if (fileType != null && fileType == 1) {
            allowedTypes = Arrays.asList(imageAllowedTypes.split(","));
        } else if (fileType != null && fileType == 2) {
            allowedTypes = Arrays.asList(videoAllowedTypes.split(","));
        }
        return allowedTypes.contains(contentType);
    }
    
    public boolean checkImageFileSize(MultipartFile file, Integer fileType) {
        if (fileType != null && fileType == 1) {
            return file.getSize() <= imageMaxSize;
        } else if (fileType != null && fileType == 2) {
            return file.getSize() <= videoMaxSize;
        }
        return false;
    }
}
