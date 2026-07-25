package com.xiaolvshu.exception;

import com.xiaolvshu.common.Result;
import com.xiaolvshu.controller.TravelAgentController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Agent SSE 接口在建立事件流之前使用真实 HTTP 错误码。 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = TravelAgentController.class)
public class TravelAgentExceptionHandler {

    @ExceptionHandler(AgentAccessException.class)
    public ResponseEntity<Result<?>> handleAccess(AgentAccessException e) {
        return ResponseEntity.status(e.getStatus())
                .body(Result.error(e.getStatus().value(), e.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Result<?>> handleValidation(Exception e) {
        String message = "参数校验失败";
        if (e instanceof MethodArgumentNotValidException validation
                && validation.getBindingResult().hasErrors()) {
            message = validation.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
        } else if (e instanceof BindException binding && binding.getBindingResult().hasErrors()) {
            message = binding.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
        }
        return ResponseEntity.badRequest().body(Result.error(400, message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<?>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Result.error(400, e.getMessage()));
    }
}
