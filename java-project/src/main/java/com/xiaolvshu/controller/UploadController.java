package com.xiaolvshu.controller;

import com.xiaolvshu.common.Result;
import com.xiaolvshu.dto.UploadResult;
import com.xiaolvshu.dto.UploadMultiResult.UploadError;
import com.xiaolvshu.dto.UploadMultiResult;
import com.xiaolvshu.service.UploadService;
import com.xiaolvshu.utils.CosUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 上传文件接口
 */
@Slf4j
@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;
    private final CosUtil cosUtil;

    /**
     * 上传单张图片
     */
    @PostMapping("/single")
    public Result<UploadResult> uploadSingleImage(@RequestParam("file") MultipartFile file) {
        log.info("上传单个图片: {}", file.getOriginalFilename());
        
        // 验证文件
        if (file.isEmpty()) {
            return Result.error("没有上传文件");
        }
        
        if (!uploadService.isValidImageFile(file)) {
            return Result.error("只允许上传图片文件");
        }
        
        if (!uploadService.checkImageFileSize(file)) {
            return Result.error("文件大小超过限制（10MB）");
        }
        
        // 上传图片
        try {
            String url = cosUtil.uploadFile(file, "images");
            UploadResult result = new UploadResult();
            result.setOriginalname(file.getOriginalFilename());
            result.setUrl(url);
            log.info("图片上传成功: {}", url);
            return Result.success(result);
        } catch (Exception e) {
            log.error("图片上传失败", e);
            return Result.error("图片上传失败: " + e.getMessage());
        }
    }

    /**
     * 上传多张图片（最多9张）
     */
    @PostMapping("/multiple")
    public Result<UploadMultiResult> uploadMultipleImages(@RequestParam("files") MultipartFile[] files) {
        log.info("上传多张图片，数量: {}", files.length);
        
        if (files.length == 0) {
            return Result.error("没有上传文件");
        }
        
        if (files.length > 9) {
            return Result.error("文件数量超过限制(9个)");
        }
        
        List<UploadResult> uploaded = new ArrayList<>();
        List<UploadError> errors = new ArrayList<>();
        
        for (MultipartFile file : files) {
            // 验证文件
            if (!uploadService.isValidImageFile(file)) {
                UploadError error = new UploadError();
                error.setFile(file.getOriginalFilename());
                error.setError("只允许上传图片文件");
                errors.add(error);
                continue;
            }
            
            if (!uploadService.checkImageFileSize(file)) {
                UploadError error = new UploadError();
                error.setFile(file.getOriginalFilename());
                error.setError("文件大小超过限制(10MB)");
                errors.add(error);
                continue;
            }
            
            // 上传图片
            try {
                String url = cosUtil.uploadFile(file, "images");
                UploadResult result = new UploadResult();
                result.setOriginalname(file.getOriginalFilename());
                result.setUrl(url);
                uploaded.add(result);
            } catch (Exception e) {
                log.error("图片上传失败: {}", file.getOriginalFilename(), e);
                UploadError error = new UploadError();
                error.setFile(file.getOriginalFilename());
                error.setError("上传失败: " + e.getMessage());
                errors.add(error);
            }
        }
        
        if (uploaded.isEmpty()) {
            return Result.error("所有图片上传失败");
        }
        
        UploadMultiResult data = new UploadMultiResult(uploaded, errors);
        
        String message = errors.isEmpty() ? "所有图片上传成功" : uploaded.size() + "张上传成功，" + errors.size() + "张失败";
        
        log.info("多图片上传完成: {}", message);
        return Result.success(message, data);
    }

    /**
     * 上传视频（支持携带缩略图）
     */
    @PostMapping("/video")
    public Result<UploadResult> uploadVideo(MultipartFile file, MultipartFile thumbnail) {
        log.info("上传视频: {}, 带缩略图: {}", file.getOriginalFilename(), thumbnail != null ? thumbnail.getOriginalFilename() : "");
        
        // 验证视频文件
        if (!uploadService.isValidVideoFile(file)) {
            return Result.error("视频格式不支持");
        }
        
        if (!uploadService.checkVideoFileSize(file)) {
            return Result.error("文件大小超过限制(100MB)");
        }
        String url;
        try {
            url = cosUtil.uploadFile(file, "videos");
            log.info("视频上传成功: {}", url);
        } catch (Exception e) {
            log.error("视频上传失败", e);
            return Result.error("视频上传失败: " + e.getMessage());
        }
        // 如果有缩略图，上传缩略图
        if (!uploadService.isValidImageFile(thumbnail)) {
            return Result.error("缩略图格式不支持");
        }
        if (!uploadService.checkImageFileSize(thumbnail)) {
            return Result.error("缩略图大小超过限制(10MB)");
        }
        String thumbnailUrl;
        try {
            thumbnailUrl = cosUtil.uploadFile(thumbnail, "images");
            log.info("缩略图上传成功: {}", thumbnailUrl);
        } catch (Exception e) {
            log.error("缩略图上传失败", e);
            return Result.error("缩略图上传失败: " + e.getMessage());
        }
        UploadResult result = new UploadResult();
        result.setOriginalname(file.getOriginalFilename());
        result.setUrl(url);
        result.setCoverUrl(thumbnailUrl);
        result.setSize(file.getSize());
        
        return Result.success(result);
    }
}