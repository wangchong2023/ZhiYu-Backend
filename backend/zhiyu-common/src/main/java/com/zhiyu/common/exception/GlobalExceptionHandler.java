package com.zhiyu.common.exception;

import com.zhiyu.common.web.ApiResponse;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;

@Slf4j
@RestControllerAdvice(basePackages = "com.zhiyu")
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleBizException(final BizException e, final HttpServletRequest request) {
        String message = resolveMessage(e);
        if (log.isWarnEnabled()) {
            log.warn("BizException: code={}, message={}", e.getCode(), message);
        }
        return ApiResponse.fail(e.getCode(), message);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleValidation(final MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElseGet(() -> messageSource.getMessage("error.40001", null, "Validation failed", LocaleContextHolder.getLocale()));
        return ApiResponse.fail(BizErrorCode.VALIDATION_FAILED.getCode(), msg);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleUnknown(final Exception e) {
        log.error("Unexpected error", e);
        String message = messageSource.getMessage("error.50001", null, "Internal server error", LocaleContextHolder.getLocale());
        return ApiResponse.fail(BizErrorCode.INTERNAL_ERROR.getCode(), message);
    }

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

        String resolved = messageSource.getMessage("error." + e.getCode(), null, defaultMessage, locale);
        return resolved != null ? resolved : defaultMessage;
    }
}
