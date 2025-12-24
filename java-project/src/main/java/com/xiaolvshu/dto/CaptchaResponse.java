package com.xiaolvshu.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 验证码生成响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaResponse {
    @JsonProperty("captchaId")
    private String captchaId;
    @JsonProperty("captchaSvg")
    private String captchaSvg;
}
