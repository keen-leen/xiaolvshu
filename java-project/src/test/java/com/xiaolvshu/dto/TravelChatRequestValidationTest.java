package com.xiaolvshu.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

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
    void shouldRejectOversizedConversationId() {
        TravelChatRequest request = validRequest();
        request.setConversationId("x".repeat(37));

        Set<ConstraintViolation<TravelChatRequest>> violations = validator.validate(request);

        assertTrue(messages(violations).contains("conversationId格式不正确"));
    }

    @Test
    void shouldAllowBackendIssuedConversationId() {
        TravelChatRequest request = validRequest();
        request.setConversationId("20b1c884-8e44-4fe9-b7bf-e2b29b1598da");

        Set<ConstraintViolation<TravelChatRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    private TravelChatRequest validRequest() {
        TravelChatRequest request = new TravelChatRequest();
        request.setMessage("成都三日游");
        request.setTopK(5);
        return request;
    }

    private Set<String> messages(Set<ConstraintViolation<TravelChatRequest>> violations) {
        return violations.stream().map(ConstraintViolation::getMessage).collect(java.util.stream.Collectors.toSet());
    }
}
