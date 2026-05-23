package com.zhiyu.auth.service;

import com.zhiyu.auth.converter.AuthConverter;
import com.zhiyu.auth.dto.*;
import com.zhiyu.auth.validator.AuthValidator;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.jwt.JwtService;
import com.zhiyu.ufp.auth.jwt.JwtClaims;
import com.zhiyu.ufp.auth.jwt.JwtService.JwtPair;
import com.zhiyu.ufp.auth.mapper.AuthUserLogMapper;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.auth.password.PasswordService;
import com.zhiyu.ufp.auth.token.TokenBlacklist;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AuthUserMapper authUserMapper;
    @Mock private AuthUserLogMapper authUserLogMapper;
    @Mock private PasswordService passwordService;
    @Mock private JwtService jwtService;
    @Mock private TokenBlacklist tokenBlacklist;
    @Mock private CaptchaService captchaService;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private AuthValidator authValidator;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @InjectMocks private AuthService authService;

    @Test
    void shouldRegisterSuccessfully() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("testuser");
        req.setPassword("Abc12345");
        req.setEmail("test@example.com");
        req.setCaptchaToken("tok");
        req.setCaptchaCode("A3x9");

        when(authUserMapper.selectOne(any())).thenReturn(null);
        when(passwordService.hash("Abc12345")).thenReturn("$2a$12$hashed");

        var resp = authService.register(req);

        assertThat(resp.getUsername()).isEqualTo("testuser");
        verify(authUserMapper).insert(any(AuthUser.class));
    }

    @Test
    void shouldLoginSuccessfully() {
        LoginRequest req = new LoginRequest();
        req.setUsername("testuser");
        req.setPassword("Abc12345");

        AuthUser user = AuthUser.builder()
                .authUserId(1001L).authUserUsername("testuser")
                .authUserPassword("$2a$12$hashed")
                .authUserEnable(1).authUserDeleted(0).build();

        when(authUserMapper.selectOne(any())).thenReturn(user);
        when(passwordService.verify("Abc12345", "$2a$12$hashed")).thenReturn(true);
        when(jwtService.issue(1001L, "testuser", "openid"))
                .thenReturn(new JwtPair("access", "refresh", 900));

        var resp = authService.login(req);

        assertThat(resp.getAccessToken()).isEqualTo("access");
        assertThat(resp.getRefreshToken()).isEqualTo("refresh");
        assertThat(resp.getTokenType()).isEqualTo("Bearer");
    }

    @Test
    void shouldFailLoginWithWrongPassword() {
        LoginRequest req = new LoginRequest();
        req.setUsername("testuser");
        req.setPassword("WrongPass1");

        AuthUser user = AuthUser.builder()
                .authUserId(1001L).authUserUsername("testuser")
                .authUserPassword("$2a$12$hashed")
                .authUserEnable(1).authUserDeleted(0).build();

        when(authUserMapper.selectOne(any())).thenReturn(user);
        when(passwordService.verify("WrongPass1", "$2a$12$hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req))
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    void shouldRefreshToken() {
        RefreshRequest req = new RefreshRequest();
        req.setRefreshToken("old-refresh");

        JwtClaims claims = new JwtClaims("1001", "iss", "aud",
                System.currentTimeMillis() / 1000 + 3600,
                System.currentTimeMillis() / 1000, "jti", "testuser", "openid");

        when(jwtService.verify("old-refresh")).thenReturn(claims);
        when(jwtService.getUserId("old-refresh")).thenReturn(1001L);
        when(jwtService.issue(1001L, "testuser", "openid"))
                .thenReturn(new JwtPair("new-access", "new-refresh", 900));

        var resp = authService.refresh(req);

        assertThat(resp.getAccessToken()).isEqualTo("new-access");
        verify(tokenBlacklist).add(eq("old-refresh"), anyLong());
    }
}
