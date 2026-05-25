package com.zhiyu.common.exception;

import com.zhiyu.common.web.ApiResponse;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import com.zhiyu.ufp.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private MessageSource messageSource;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private GlobalExceptionHandler handler;

    @Test
    void shouldHandleBizExceptionWithI18n() {
        BizException ex = new BizException(BizErrorCode.TOKEN_EXPIRED);
        when(messageSource.getMessage(eq("error.40101"), any(), eq("Access token expired"), any()))
                .thenReturn("访问令牌已过期");

        ApiResponse<Void> resp = handler.handleBizException(ex, request);

        assertThat(resp.getCode()).isEqualTo(40101);
        assertThat(resp.getMessage()).isEqualTo("访问令牌已过期");
    }

    @Test
    void shouldHandleBizExceptionWithoutErrorCode() {
        BizException ex = new BizException(40099, "Custom error");

        ApiResponse<Void> resp = handler.handleBizException(ex, request);

        assertThat(resp.getCode()).isEqualTo(40099);
        assertThat(resp.getMessage()).isEqualTo("Custom error");
    }

    @Test
    void shouldHandleValidationException() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("loginRequest", "username", "用户名不能为空");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ApiResponse<Void> resp = handler.handleValidation(ex);

        assertThat(resp.getCode()).isEqualTo(40001);
        assertThat(resp.getMessage()).contains("username");
        assertThat(resp.getMessage()).contains("用户名不能为空");
    }

    @Test
    void shouldHandleValidationExceptionWithNoFieldErrors() {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of());
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);
        when(messageSource.getMessage(eq("error.40001"), any(), eq("Validation failed"), any()))
                .thenReturn("验证失败");

        ApiResponse<Void> resp = handler.handleValidation(ex);

        assertThat(resp.getCode()).isEqualTo(40001);
        assertThat(resp.getMessage()).isEqualTo("验证失败");
    }

    @Test
    void shouldHandleUnknownException() {
        when(messageSource.getMessage(eq("error.50001"), any(), eq("Internal server error"), any()))
                .thenReturn("服务器内部错误");

        ApiResponse<Void> resp = handler.handleUnknown(new RuntimeException("boom"));

        assertThat(resp.getCode()).isEqualTo(BizErrorCode.INTERNAL_ERROR.getCode());
        assertThat(resp.getMessage()).isEqualTo("服务器内部错误");
    }

    @Test
    void shouldFallbackToExceptionMessageWhenNoI18nKey() {
        BizErrorCode codeWithoutI18n = BizErrorCode.INTERNAL_ERROR;
        BizException ex = new BizException(codeWithoutI18n);
        when(messageSource.getMessage(eq("error.50001"), any(), eq("Internal server error, please try again later"), any()))
                .thenReturn("Internal server error, please try again later");

        ApiResponse<Void> resp = handler.handleBizException(ex, request);

        assertThat(resp.getMessage()).isEqualTo("Internal server error, please try again later");
    }

    @Test
    void shouldFallbackToExceptionMessageWhenErrorCodeHasNullI18nKey() {
        ErrorCode codeWithNullI18n = new ErrorCode() {
            @Override
            public int getCode() { return 40999; }
            @Override
            public String getMessage() { return "Default message"; }
        };
        BizException ex = new BizException(codeWithNullI18n);

        ApiResponse<Void> resp = handler.handleBizException(ex, request);

        assertThat(resp.getCode()).isEqualTo(40999);
        assertThat(resp.getMessage()).isEqualTo("Default message");
    }
}
