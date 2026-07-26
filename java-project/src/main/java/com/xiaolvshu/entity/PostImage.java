package com.xiaolvshu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 笔记图片实体
 */
@Data
@TableName("post_images")
public class PostImage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 图片ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 笔记ID
     */
    @TableField("post_id")
    private Long postId;
    
    /**
     * 图片URL
     */
    @TableField("image_url")
    private String imageUrl;

    /**
     * 同一笔记内的展示顺序。
     *
     * <p>不能依赖数据库自增 ID 隐式排序，否则重新导入或迁移图片后首图可能变化。</p>
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 图片提供方；公开演示种子数据固定为 pexels。 */
    @TableField("provider")
    private String provider;

    /** 提供方资源 ID，用于检测同一图片被重复分配。 */
    @TableField("provider_asset_id")
    private String providerAssetId;

    /** 摄影师署名。 */
    @TableField("photographer")
    private String photographer;

    /** 摄影师在图片提供方的主页。 */
    @TableField("photographer_url")
    private String photographerUrl;

    /** 图片在提供方的原始详情页，而不是 CDN 图片地址。 */
    @TableField("source_url")
    private String sourceUrl;

    /** 采集图片时适用的许可证名称。 */
    @TableField("license_name")
    private String licenseName;

    /** 许可证说明页。 */
    @TableField("license_url")
    private String licenseUrl;

    /** 无障碍替代文本；种子数据优先使用 Pexels API 返回的 alt。 */
    @TableField("alt_text")
    private String altText;
}
