package com.example.bio_platform.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice // 全局拦截RestController的异常
public class GlobalExceptionHandler {

    // 处理运行时异常
    @ExceptionHandler(RuntimeException.class)
    public Result<?> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常：", e);
        return Result.error(e.getMessage());
    }

    // 处理空指针异常
    @ExceptionHandler(NullPointerException.class)
    public Result<?> handleNullPointerException(NullPointerException e) {
        log.error("空指针异常：", e);
        return Result.error("系统异常：空指针");
    }

    // 处理通用异常
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("通用异常：", e);
        return Result.error("系统内部异常，请联系管理员");
    }
}