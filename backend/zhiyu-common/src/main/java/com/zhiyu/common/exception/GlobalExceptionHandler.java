/**
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: GlobalExceptionHandler.java
 * 创建时间: 2026-05-27
 * 描述: 全局异常处理器，拦截控制层抛出的各类已知业务异常与未知系统异常，支持 i18n 资源文件统一检索和格式化返回。
 */
package com.zhiyu.common.exception;

import com.zhiyu.common.web.ApiResponse;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import com.zhiyu.ufp.common.exception.ErrorConvertCustomize;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 类名: GlobalExceptionHandler
 * 描述: 基于 @RestControllerAdvice 实现的全局统一异常拦截切面。处理业务异常、方法参数绑定校验异常以及未知系统内部错误。
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.zhiyu")
public class GlobalExceptionHandler {

    // i18n 错误配置资源前缀
    private static final String I18N_ERROR_PREFIX = "error.";

    private final MessageSource messageSource;
    private final List<ErrorConvertCustomize> converters;

    /**
     * 描述: 构造函数，注入 MessageSource 资源及定制化异常转换器集合
     * @param messageSource i18n 资源文件查找源
     * @param converters 异常自定义转换机制链
     */
    public GlobalExceptionHandler(final MessageSource messageSource,
                                  final List<ErrorConvertCustomize> converters) {
        this.messageSource = messageSource;
        this.converters = converters != null ? converters : List.of();
    }

    /**
     * 描述: 拦截并处理主动抛出的已知业务异常 (BizException)，自动从 i18n 资源文件中依据异常码及语言环境检索返回文案。
     * @param e 业务异常实例
     * @param request 请求上下文环境
     * @return 格式化的 API 响应失败包载体
     */
    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleBizException(final BizException e, final HttpServletRequest request) {
        String message = resolveMessage(e);
        if (log.isWarnEnabled()) {
            log.warn("BizException: code={}, message={}", e.getCode(), message);
        }
        return ApiResponse.fail(e.getCode(), message);
    }

    /**
     * 描述: 拦截并处理参数验证失败异常 (@Valid / MethodArgumentNotValidException)，提取具体的字段校验错误信息。
     * @param e 校验异常实例
     * @return 包含具体字段报错描述的 API 响应失败包载体
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleValidation(final MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElseGet(() -> {
                    String i18nKey = I18N_ERROR_PREFIX + BizErrorCode.VALIDATION_FAILED.getCode();
                    return messageSource.getMessage(
                            i18nKey, null, "Validation failed",
                            LocaleContextHolder.getLocale());
                });
        return ApiResponse.fail(BizErrorCode.VALIDATION_FAILED.getCode(), msg);
    }

    /**
     * 描述: 兜底拦截并处理所有未被显式捕获的普通 Exception 系统异常。
     * @param e 异常实例
     * @return 返回 500 服务器内部错误的 API 响应失败包载体
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleUnknown(final Exception e) {
        for (ErrorConvertCustomize converter : converters) {
            Map<String, Object> result = converter.convert(e);
            if (result != null) {
                int code = (int) result.getOrDefault("code", BizErrorCode.INTERNAL_ERROR.getCode());
                String msg = (String) result.getOrDefault("message", e.getMessage());
                log.error("Converted error: code={}, message={}", code, msg, e);
                return ApiResponse.fail(code, msg);
            }
        }
        log.error("Unexpected error", e);
        String i18nKey = I18N_ERROR_PREFIX + BizErrorCode.INTERNAL_ERROR.getCode();
        String message = messageSource.getMessage(
                i18nKey, null, "Internal server error",
                LocaleContextHolder.getLocale());
        return ApiResponse.fail(BizErrorCode.INTERNAL_ERROR.getCode(), message);
    }

    /**
     * 描述: 解析业务异常对应的本地化多语言描述文字信息。
     */
    private String resolveMessage(final BizException e) {
        Locale locale = LocaleContextHolder.getLocale();
        String defaultMessage = e.getMessage();

        if (e.getErrorCode() != null) {
            String i18nKey = e.getErrorCode().getI18nKey();
            if (i18nKey != null) {
                String resolved = messageSource.getMessage(i18nKey, null, defaultMessage, locale);
                if (resolved != null) {
                    return resolved;
                }
            }
        }

        String resolved = messageSource.getMessage(I18N_ERROR_PREFIX + e.getCode(), null, defaultMessage, locale);
        return resolved != null ? resolved : defaultMessage;
    }
}
