package com.xiaolvshu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单文件上传结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadResult {
    private String originalname;
    private Long size;
    private String url;
    private String filePath;
    private String coverUrl;
    
    public UploadResult(String originalname, Long size, String url) {
        this.originalname = originalname;
        this.size = size;
        this.url = url;
    }
    
    /**
     * 多文件上传结果
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MultiUploadResult {
        private List<UploadResult> uploaded;
        private List<UploadError> errors;
        private Integer total;
        private Integer successCount;
        private Integer errorCount;
    }
    
    /**
     * 上传错误信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadError {
        private String file;
        private String error;
    }
}
