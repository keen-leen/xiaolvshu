package com.xiaolvshu.dto;

import lombok.Data;
import java.util.List;

/**
 * 多文件上传结果
 */
@Data
public class UploadMultiResult {
    private List<UploadResult> uploaded;
    private List<UploadError> errors;
    private Integer total;
    private Integer successCount;
    private Integer errorCount;

    @Data
    public static class UploadError {
        private String file;
        private String error; 
    }

    public UploadMultiResult() {
    } 

    public UploadMultiResult(List<UploadResult> uploaded, List<UploadError> errors) {
        this.uploaded = uploaded;
        this.errors = errors;
        this.total = (uploaded != null ? uploaded.size() : 0) + (errors != null ? errors.size() : 0);
        this.successCount = uploaded != null ? uploaded.size() : 0;
        this.errorCount = errors != null ? errors.size() : 0;
    }
}
