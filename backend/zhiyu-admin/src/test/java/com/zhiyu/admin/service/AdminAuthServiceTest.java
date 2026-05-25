package com.zhiyu.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.auth.dto.LoginRequest;
import com.zhiyu.auth.dto.LoginResponse;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.jwt.JwtService.JwtPair;
import com.zhiyu.ufp.auth.password.PasswordService;
import com.zhiyu.ufp.auth.service.IAuthUserService;
import com.zhiyu.ufp.auth.spi.AuthFlowManager;
import com.zhiyu.ufp.auth.spi.AuthFlowResult;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    @Mock private IAuthUserService authUserService;
    @Mock private PasswordService passwordService;
    @Mock private AuthFlowManager authFlowManager;
    @InjectMocks private AdminAuthService adminAuthService;

    @Test
    void shouldLoginAsAdmin() {
        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("Admin1234");
        req.setPrivacyConsent(true);

        AuthUser user = AuthUser.builder()
                .authUserId(1L).authUserUsername("admin")
                .authUserPassword("$2a$12$hashed")
                .authUserScope("ADMIN")
                .authUserEnable(1).authUserDeleted(0)
                .build();

        when(authUserService.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordService.verify("Admin1234", "$2a$12$hashed")).thenReturn(true);
        when(authFlowManager.finalizeLogin(any(AuthFlowResult.class)))
                .thenReturn(new JwtPair("access-token", "refresh-token", 900));

        LoginResponse resp = adminAuthService.login(req);

        assertThat(resp.getAccessToken()).isEqualTo("access-token");
        assertThat(resp.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(resp.getExpiresIn()).isEqualTo(900);
        assertThat(resp.getTokenType()).isEqualTo("Bearer");
        assertThat(resp.getTotpRequired()).isFalse();
    }

    @Test
    void shouldRejectWrongPassword() {
        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("WrongPass1");
        req.setPrivacyConsent(true);

        AuthUser user = AuthUser.builder()
                .authUserId(1L).authUserUsername("admin")
                .authUserPassword("$2a$12$hashed")
                .authUserScope("ADMIN")
                .authUserEnable(1).authUserDeleted(0)
                .build();

        when(authUserService.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordService.verify("WrongPass1", "$2a$12$hashed")).thenReturn(false);

        assertThatThrownBy(() -> adminAuthService.login(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Incorrect password");
    }

    @Test
    void shouldRejectNonexistentUser() {
        LoginRequest req = new LoginRequest();
        req.setUsername("ghost");
        req.setPassword("Whatever1");
        req.setPrivacyConsent(true);

        when(authUserService.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> adminAuthService.login(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Incorrect password");
    }

    @Test
    void shouldRejectNonAdminUser() {
        LoginRequest req = new LoginRequest();
        req.setUsername("user");
        req.setPassword("User12345");
        req.setPrivacyConsent(true);

        AuthUser user = AuthUser.builder()
                .authUserId(2L).authUserUsername("user")
                .authUserPassword("$2a$12$hashed")
                .authUserScope("openid")
                .authUserEnable(1).authUserDeleted(0)
                .build();

        when(authUserService.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordService.verify("User12345", "$2a$12$hashed")).thenReturn(true);

        assertThatThrownBy(() -> adminAuthService.login(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void shouldRejectDisabledUser() {
        LoginRequest req = new LoginRequest();
        req.setUsername("disabled");
        req.setPassword("Disabled1");
        req.setPrivacyConsent(true);

        AuthUser user = AuthUser.builder()
                .authUserId(3L).authUserUsername("disabled")
                .authUserPassword("$2a$12$hashed")
                .authUserScope("ADMIN")
                .authUserEnable(0).authUserDeleted(0)
                .build();

        when(authUserService.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordService.verify("Disabled1", "$2a$12$hashed")).thenReturn(true);

        assertThatThrownBy(() -> adminAuthService.login(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Account has been disabled");
    }

    @Test
    void shouldRejectUserWithNullEnable() {
        LoginRequest req = new LoginRequest();
        req.setUsername("nullenable");
        req.setPassword("NullEnab1");
        req.setPrivacyConsent(true);

        AuthUser user = AuthUser.builder()
                .authUserId(4L).authUserUsername("nullenable")
                .authUserPassword("$2a$12$hashed")
                .authUserScope("ADMIN")
                .authUserEnable(null).authUserDeleted(0)
                .build();

        when(authUserService.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordService.verify("NullEnab1", "$2a$12$hashed")).thenReturn(true);

        assertThatThrownBy(() -> adminAuthService.login(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Account has been disabled");
    }

    @Test
    void shouldRejectMissingPrivacyConsent() {
        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("Admin1234");

        assertThatThrownBy(() -> adminAuthService.login(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Please agree to the Privacy Policy");
    }

    @Test
    void shouldRejectFalsePrivacyConsent() {
        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("Admin1234");
        req.setPrivacyConsent(false);

        assertThatThrownBy(() -> adminAuthService.login(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Please agree to the Privacy Policy");
    }
}
