package com.xiaolvshu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 点赞消息 DTO
 * 用于 RabbitMQ 异步传递点赞/取消点赞事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LikeMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 点赞用户ID
     */
    private Long userId;

    /**
     * 目标ID（笔记ID或评论ID）
     */
    private Long targetId;

    /**
     * 目标类型: 1-笔记, 2-评论
     */
    private Integer targetType;

    /**
     * 操作类型: LIKE-点赞, UNLIKE-取消点赞
     */
    private String action;

    // ============ 操作类型常量 ============

    public static final String ACTION_LIKE = "LIKE";
    public static final String ACTION_UNLIKE = "UNLIKE";
}
