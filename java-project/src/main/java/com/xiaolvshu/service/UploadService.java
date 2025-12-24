package com.xiaolvshu.service;

import cn.hutool.core.util.StrUtil;
import com.xiaolvshu.config.UploadProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
