package com.library.controller.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * REST API 统一异常处理：把校验失败、业务参数错误转成统一的 JSON 结构
 * {"code":..,"message":..}。只作用于 api 包下的控制器，不影响页面端。
 */
@RestControllerAdvice(basePackages = "com.library.controller.api")
public class ApiExceptionHandler {

    public record ApiError(int code, String message) {
    }

    /** @Valid 校验失败 → 400，取第一条错误的文案。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError onValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldError() == null
                ? "参数不合法"
                : e.getBindingResult().getFieldError().getDefaultMessage();
        return new ApiError(400, message);
    }

    /** 业务参数错误（如日期格式不对）→ 400。 */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError onIllegalArgument(IllegalArgumentException e) {
        return new ApiError(400, e.getMessage() == null ? "参数不合法" : e.getMessage());
    }

    /** JSON 请求体解析失败 → 400。 */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError onUnreadable(org.springframework.http.converter.HttpMessageNotReadableException e) {
        return new ApiError(400, "请求体不是合法的 JSON");
    }
}
