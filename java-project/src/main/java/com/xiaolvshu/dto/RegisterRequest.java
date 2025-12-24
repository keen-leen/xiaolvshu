package com.xiaolvshu.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    
    @NotBlank(message = "小旅书不能为空")
    @Size(min = 3, max = 15, message = "小旅书号长度必须在3-15位之间")
    private String userId;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 10, message = "昵称长度必须少于10位")
    private String nickname;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度必须在6-20位之间")
    private String password;

    @NotBlank(message = "验证码ID不能为空")
    @JsonProperty("captchaId")
    private String captchaId;

    @NotBlank(message = "验证码不能为空")
    @JsonProperty("captchaText")
    private String captchaText;

    private String bio;
}
