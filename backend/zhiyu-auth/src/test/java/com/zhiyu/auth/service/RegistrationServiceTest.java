package com.zhiyu.auth.service;

import com.zhiyu.auth.dto.RegisterRequest;
import com.zhiyu.auth.dto.RegisterResponse;
import com.zhiyu.auth.validator.AuthValidator;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.service.IAuthUserService;
import com.zhiyu.ufp.auth.password.PasswordService;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock private IAuthUserService authUserService;
    @Mock private PasswordService passwordService;
    @Mock private CaptchaService captchaService;
    @Mock private AuthValidator authValidator;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks private RegistrationService registrationService;

    private RegisterRequest validRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("testuser");
        req.setPassword("Abc12345");
        req.setEmail("test@example.com");
        req.setVerifyCode("123456");
        req.setCaptchaToken("tok");
        req.setCaptchaCode("A3x9");
        return req;
    }

    @Test
    void shouldRegisterSuccessfully() {
        RegisterRequest req = validRequest();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("123456");
        when(authUserService.selectOne(any())).thenReturn(null);
        when(passwordService.hash("Abc12345")).thenReturn("$2a$12$hashed");

        RegisterResponse resp = registrationService.register(req);

        assertThat(resp.getUsername()).isEqualTo("testuser");
        ArgumentCaptor<AuthUser> captor = ArgumentCaptor.forClass(AuthUser.class);
        verify(authUserService).insert(captor.capture());
        assertThat(captor.getValue().getAuthUserPassword()).isEqualTo("$2a$12$hashed");
    }

    @Test
    void shouldFailRegisterWithDuplicateUsername() {
        RegisterRequest req = validRequest();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("123456");
        when(authUserService.selectOne(any()))
                .thenReturn(AuthUser.builder().authUserId(1L).build());

        assertThatThrownBy(() -> registrationService.register(req))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.USERNAME_TAKEN.getCode());
    }

    @Test
    void shouldFailRegisterWithDuplicateEmail() {
        RegisterRequest req = validRequest();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("123456");
        when(authUserService.selectOne(any()))
                .thenReturn(null)
                .thenReturn(AuthUser.builder().authUserId(2L).build());

        assertThatThrownBy(() -> registrationService.register(req))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.EMAIL_TAKEN.getCode());
    }

    @Test
    void shouldFailRegisterWithIncorrectVerifyCode() {
        RegisterRequest req = validRequest();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("999999");

        assertThatThrownBy(() -> registrationService.register(req))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.VERIFY_CODE_INCORRECT.getCode());
    }

    @Test
    void shouldFailRegisterWithMissingVerifyCode() {
        RegisterRequest req = validRequest();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThatThrownBy(() -> registrationService.register(req))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.VERIFY_CODE_INCORRECT.getCode());
    }
}
