package com.xiaolvshu.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TravelChatRequest {

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息内容不能超过2000个字符")
    private String message;

    @Min(value = 1, message = "topK不能小于1")
    @Max(value = 10, message = "topK不能大于10")
    private Integer topK = 5;

    /**
     * 会话 ID 由后端首次签发，后续请求原样带回。这里只限制 UUID 文本长度，
     * 具体格式统一由会话服务解析，避免 Controller、历史查询和清空接口各自实现一套规则。
     */
    @Size(max = 36, message = "conversationId格式不正确")
    private String conversationId;
}
