package com.xiaolvshu.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * 单文件上传结果
 */
@Data
public class UploadResult {
    private String originalname;
    private Long size;
    private String url;
    private String filePath;
    @JsonProperty("coverUrl")
    private String coverUrl;

    public UploadResult() {
    }
    
    public UploadResult(String originalname, Long size, String url) {
        this.originalname = originalname;
        this.size = size;
        this.url = url;
    }
}
