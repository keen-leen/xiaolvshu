package com.xiaolvshu.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelChatRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldRejectOversizedMessageAndInvalidTopK() {
        TravelChatRequest request = new TravelChatRequest();
        request.setMessage("x".repeat(2001));
        request.setTopK(11);

        Set<ConstraintViolation<TravelChatRequest>> violations = validator.validate(request);

        assertTrue(messages(violations).contains("消息内容不能超过2000个字符"));
        assertTrue(messages(violations).contains("topK不能大于10"));
    }

    @Test
    void shouldRejectInvalidHistoryRoleAndTooManyMessages() {
        TravelChatRequest request = validRequest();
        List<TravelChatRequest.ChatMessage> history = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            history.add(message(i == 0 ? "system" : "user", "hello"));
        }
        request.setHistory(history);

        Set<ConstraintViolation<TravelChatRequest>> violations = validator.validate(request);

        assertTrue(messages(violations).contains("历史消息不能超过8条"));
        assertTrue(messages(violations).contains("历史消息角色只能是user或assistant"));
    }

    @Test
    void shouldRejectOversizedHistoryTotalButAllowBlankStreamingPlaceholder() {
        TravelChatRequest request = validRequest();
        request.setHistory(List.of(
                message("user", "x".repeat(2_000)),
                message("assistant", "x".repeat(2_000)),
                message("user", "x".repeat(2_000)),
                message("assistant", "x".repeat(2_000)),
                message("user", "x".repeat(2_000)),
                message("assistant", "x".repeat(2_000)),
                message("user", "x"),
                message("assistant", "")));

        Set<ConstraintViolation<TravelChatRequest>> violations = validator.validate(request);

        assertTrue(messages(violations).contains("历史消息总长度不能超过12000个字符"));
    }

    private TravelChatRequest validRequest() {
        TravelChatRequest request = new TravelChatRequest();
        request.setMessage("成都三日游");
        request.setTopK(5);
        return request;
    }

    private TravelChatRequest.ChatMessage message(String role, String content) {
        TravelChatRequest.ChatMessage message = new TravelChatRequest.ChatMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private Set<String> messages(Set<ConstraintViolation<TravelChatRequest>> violations) {
        return violations.stream().map(ConstraintViolation::getMessage).collect(java.util.stream.Collectors.toSet());
    }
}
