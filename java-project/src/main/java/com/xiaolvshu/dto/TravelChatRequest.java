package com.xiaolvshu.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class TravelChatRequest {

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息内容不能超过2000个字符")
    private String message;

    @Min(value = 1, message = "topK不能小于1")
    @Max(value = 10, message = "topK不能大于10")
    private Integer topK = 5;

    @Size(max = 8, message = "历史消息不能超过8条")
    private List<@Valid ChatMessage> history;

    /**
     * 历史消息还需要限制总长度，否则即使单条都合法，8 条长消息仍可以大幅增加模型费用。
     * 空白历史允许进入后续清理流程，因为流式前端在助手消息尚未产生时可能暂时带上空内容。
     */
    @JsonIgnore
    @AssertTrue(message = "历史消息总长度不能超过12000个字符")
    public boolean isHistoryLengthValid() {
        if (history == null || history.isEmpty()) {
            return true;
        }
        long totalLength = history.stream()
                .filter(item -> item != null && item.getContent() != null)
                .mapToLong(item -> item.getContent().length())
                .sum();
        return totalLength <= 12_000L;
    }

    @Data
    public static class ChatMessage {
        @NotBlank(message = "历史消息角色不能为空")
        @Pattern(regexp = "(?i)user|assistant", message = "历史消息角色只能是user或assistant")
        private String role;

        @Size(max = 2000, message = "单条历史消息不能超过2000个字符")
        private String content;
    }
}
