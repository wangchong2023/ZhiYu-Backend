package com.zhiyu.admin.controller;

import com.zhiyu.admin.service.AdminAuthService;
import com.zhiyu.auth.dto.LoginRequest;
import com.zhiyu.auth.dto.LoginResponse;
import com.zhiyu.common.web.ApiResponse;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuthControllerTest {

    @Mock
    private AdminAuthService adminAuthService;

    @InjectMocks
    private AdminAuthController adminAuthController;

    private LoginRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new LoginRequest();
        validRequest.setUsername("admin");
        validRequest.setPassword("Admin1234");
        validRequest.setPrivacyConsent(true);
    }

    // ─── Happy path ───

    @Test
    void shouldReturnLoginResponseWhenCredentialsValid() {
        LoginResponse mockResponse = LoginResponse.builder()
                .accessToken("access-token-xyz")
                .refreshToken("refresh-token-xyz")
                .expiresIn(900L)
                .tokenType("Bearer")
                .totpRequired(false)
                .build();

        when(adminAuthService.login(any(LoginRequest.class))).thenReturn(mockResponse);

        ApiResponse<LoginResponse> response = adminAuthController.login(validRequest);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getAccessToken()).isEqualTo("access-token-xyz");
        assertThat(response.getData().getRefreshToken()).isEqualTo("refresh-token-xyz");
        assertThat(response.getData().getExpiresIn()).isEqualTo(900L);
        assertThat(response.getData().getTokenType()).isEqualTo("Bearer");
        assertThat(response.getData().getTotpRequired()).isFalse();
        assertThat(response.getRequestId()).isNotBlank();
        assertThat(response.getTimestamp()).isPositive();

        verify(adminAuthService).login(validRequest);
        verifyNoMoreInteractions(adminAuthService);
    }

    @Test
    void shouldReturnTotpRequiredWhenTotpEnabled() {
        LoginResponse mockResponse = LoginResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .expiresIn(900L)
                .tokenType("Bearer")
                .totpRequired(true)
                .build();

        when(adminAuthService.login(any(LoginRequest.class))).thenReturn(mockResponse);

        ApiResponse<LoginResponse> response = adminAuthController.login(validRequest);

        assertThat(response.getData().getTotpRequired()).isTrue();
        verify(adminAuthService).login(validRequest);
    }

    @Test
    void shouldReturnIsNewUserWhenNewlyRegistered() {
        LoginResponse mockResponse = LoginResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .expiresIn(900L)
                .tokenType("Bearer")
                .totpRequired(false)
                .isNewUser(true)
                .build();

        when(adminAuthService.login(any(LoginRequest.class))).thenReturn(mockResponse);

        ApiResponse<LoginResponse> response = adminAuthController.login(validRequest);

        assertThat(response.getData().getIsNewUser()).isTrue();
        verify(adminAuthService).login(validRequest);
    }

    // ─── Error propagation ───

    @Test
    void shouldPropagateBadCredentialsException() {
        when(adminAuthService.login(any(LoginRequest.class)))
                .thenThrow(new BizException(BizErrorCode.INCORRECT_PASSWORD));

        assertThatThrownBy(() -> adminAuthController.login(validRequest))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Incorrect password");

        verify(adminAuthService).login(validRequest);
    }

    @Test
    void shouldPropagateForbiddenException() {
        when(adminAuthService.login(any(LoginRequest.class)))
                .thenThrow(new BizException(BizErrorCode.ACCESS_DENIED));

        assertThatThrownBy(() -> adminAuthController.login(validRequest))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Access denied");

        verify(adminAuthService).login(validRequest);
    }

    @Test
    void shouldPropagateAccountDisabledException() {
        when(adminAuthService.login(any(LoginRequest.class)))
                .thenThrow(new BizException(BizErrorCode.ACCOUNT_DISABLED));

        assertThatThrownBy(() -> adminAuthController.login(validRequest))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Account has been disabled");

        verify(adminAuthService).login(validRequest);
    }

    // ─── Request passthrough ───

    @Test
    void shouldPassRequestToService() {
        LoginRequest customRequest = new LoginRequest();
        customRequest.setUsername("superadmin");
        customRequest.setPassword("SuperAdmin1!");
        customRequest.setCaptchaToken("captcha-token");
        customRequest.setCaptchaCode("123456");
        customRequest.setPrivacyConsent(true);

        LoginResponse mockResponse = LoginResponse.builder()
                .accessToken("token")
                .refreshToken("refresh")
                .expiresIn(900L)
                .tokenType("Bearer")
                .totpRequired(false)
                .build();

        when(adminAuthService.login(customRequest)).thenReturn(mockResponse);

        ApiResponse<LoginResponse> response = adminAuthController.login(customRequest);

        assertThat(response).isNotNull();
        verify(adminAuthService).login(customRequest);
    }
}
