package com.xiaolvshu.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LikeRequest {
    @NotNull
    private Long targetId;       // 目标ID
    @NotNull
    private Integer targetType;  // 目标类型（1-笔记，2-评论）
}
