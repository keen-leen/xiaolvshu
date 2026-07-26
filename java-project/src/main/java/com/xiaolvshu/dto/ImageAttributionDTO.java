package com.xiaolvshu.dto;

import lombok.Data;

/**
 * 图片来源与署名信息。
 *
 * <p>该对象是对现有 {@code images: string[]} 的增量补充，不替换旧字段，避免前端和第三方调用方
 * 因响应结构变化而中断。公开演示页使用这些字段链接到 Pexels 和摄影师原始页面。</p>
 */
@Data
public class ImageAttributionDTO {

    private String imageUrl;
    private String provider;
    private String providerAssetId;
    private String photographer;
    private String photographerUrl;
    private String sourceUrl;
    private String licenseName;
    private String licenseUrl;
    private String altText;
}
