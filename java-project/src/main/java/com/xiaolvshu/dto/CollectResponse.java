package com.xiaolvshu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 收藏响应DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CollectResponse {
    /**
     * 是否已收藏
     */
    private boolean collected;
}
