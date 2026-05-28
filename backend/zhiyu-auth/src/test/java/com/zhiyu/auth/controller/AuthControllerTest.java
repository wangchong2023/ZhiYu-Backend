package com.zhiyu.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.auth.dto.LoginRequest;
import com.zhiyu.auth.dto.LoginResponse;
import com.zhiyu.auth.dto.RefreshRequest;
import com.zhiyu.auth.dto.RegisterRequest;
import com.zhiyu.auth.dto.RegisterResponse;
import com.zhiyu.auth.dto.CarrierLoginRequest;
import com.zhiyu.auth.dto.GuestLoginRequest;
import com.zhiyu.auth.service.AuthService;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new TestExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @org.springframework.web.bind.annotation.RestControllerAdvice
    static class TestExceptionHandler {
        @ExceptionHandler(BizException.class)
        @ResponseStatus(HttpStatus.OK)
        public Map<String, Object> handleBizException(final BizException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", e.getCode());
            body.put("message", e.getMessage());
            body.put("data", null);
            return body;
        }
    }

    // ── Register ───────────────────────────────────────────────

    @Test
    void shouldReturnRegisterResponseWhenValidRequest() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPassword("Abc12345");
        request.setEmail("test@example.com");
        request.setVerifyCode("123456");
        request.setCaptchaToken("captcha-token");
        request.setCaptchaCode("A3x9");

        RegisterResponse response = RegisterResponse.builder()
                .userId(1001L).username("testuser").build();
        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.userId").value(1001))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNumber());

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    void shouldReturn400WhenRegisterRequestBodyIsEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenRegisterRequestBodyHasBlankUsername() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("");
        request.setPassword("Abc12345");
        request.setEmail("test@example.com");
        request.setVerifyCode("123456");
        request.setCaptchaToken("captcha-token");
        request.setCaptchaCode("A3x9");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenRegisterRequestBodyHasInvalidEmail() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPassword("Abc12345");
        request.setEmail("not-an-email");
        request.setVerifyCode("123456");
        request.setCaptchaToken("captcha-token");
        request.setCaptchaCode("A3x9");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── Login ──────────────────────────────────────────────────

    @Test
    void shouldReturnLoginResponseWhenValidCredentials() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("Abc12345");

        LoginResponse response = LoginResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .expiresIn(900)
                .tokenType("Bearer")
                .totpRequired(false)
                .build();
        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.totpRequired").value(false))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNumber());

        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    void shouldReturnValidationErrorWhenLoginRequestBodyIsEmpty() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BizException(BizErrorCode.VALIDATION_FAILED));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(BizErrorCode.VALIDATION_FAILED.getCode()));
    }

    @Test
    void shouldReturnValidationErrorWhenLoginRequestHasBlankUsername() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BizException(BizErrorCode.VALIDATION_FAILED));

        LoginRequest request = new LoginRequest();
        request.setUsername("");
        request.setPassword("Abc12345");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(BizErrorCode.VALIDATION_FAILED.getCode()));
    }

    @Test
    void shouldReturnValidationErrorWhenLoginRequestHasBlankPassword() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BizException(BizErrorCode.VALIDATION_FAILED));

        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(BizErrorCode.VALIDATION_FAILED.getCode()));
    }

    // ── Refresh ────────────────────────────────────────────────

    @Test
    void shouldReturnRefreshedTokenPairWhenValidRefreshToken() throws Exception {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("old-refresh-token");

        LoginResponse response = LoginResponse.builder()
                .accessToken("new-access")
                .refreshToken("new-refresh")
                .expiresIn(900)
                .tokenType("Bearer")
                .totpRequired(false)
                .build();
        when(authService.refresh(any(RefreshRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.accessToken").value("new-access"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNumber());

        verify(authService).refresh(any(RefreshRequest.class));
    }

    @Test
    void shouldReturn400WhenRefreshRequestIsEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── Logout ─────────────────────────────────────────────────

    @Test
    void shouldLogoutWhenValidBearerTokenAndRefreshToken() throws Exception {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("refresh-token");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNumber());

        verify(authService).logout(eq("access-token"), eq("refresh-token"));
    }

    @Test
    void shouldLogoutWhenAuthorizationHeaderIsNotBearer() throws Exception {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("refresh-token");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Basic some-value")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(authService).logout(isNull(), eq("refresh-token"));
    }

    @Test
    void shouldLogoutWhenAuthorizationHeaderIsMissing() throws Exception {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("refresh-token");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(authService).logout(isNull(), eq("refresh-token"));
    }

    @Test
    void shouldLogoutWhenRequestBodyIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk());

        verify(authService).logout(eq("access-token"), isNull());
    }

    @Test
    void shouldLogoutWhenBothHeaderAndBodyAreMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk());

        verify(authService).logout(isNull(), isNull());
    }

    // ── Carrier & Guest Login ─────────────────────────────────

    @Test
    void shouldReturnLoginResponseWhenCarrierLoginIsValid() throws Exception {
        CarrierLoginRequest request = new CarrierLoginRequest();
        request.setCarrierToken("valid_carrier_token");
        request.setAppKey("ios_app_key_123");
        request.setPrivacyConsent(true);

        LoginResponse response = LoginResponse.builder()
                .accessToken("carrier-access-token")
                .refreshToken("carrier-refresh-token")
                .expiresIn(900)
                .tokenType("Bearer")
                .isNewUser(false)
                .build();
        when(authService.carrierLogin(any(CarrierLoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/carrier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").value("carrier-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("carrier-refresh-token"));

        verify(authService).carrierLogin(any(CarrierLoginRequest.class));
    }

    @Test
    void shouldReturn400WhenCarrierLoginHasBlankToken() throws Exception {
        CarrierLoginRequest request = new CarrierLoginRequest();
        request.setCarrierToken("");
        request.setAppKey("ios_app_key_123");
        request.setPrivacyConsent(true);

        mockMvc.perform(post("/api/v1/auth/carrier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnLoginResponseWhenGuestLoginWithoutBody() throws Exception {
        LoginResponse response = LoginResponse.builder()
                .accessToken("guest-access-token")
                .refreshToken("guest-refresh-token")
                .expiresIn(900)
                .tokenType("Bearer")
                .build();
        when(authService.guestLogin(isNull())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/guest")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").value("guest-access-token"));

        verify(authService).guestLogin(isNull());
    }

    @Test
    void shouldReturnLoginResponseWhenGuestLoginWithBody() throws Exception {
        GuestLoginRequest request = new GuestLoginRequest();
        request.setDeviceId("device_identifier_789");
        request.setPrivacyConsent(true);

        LoginResponse response = LoginResponse.builder()
                .accessToken("guest-access-token")
                .refreshToken("guest-refresh-token")
                .expiresIn(900)
                .tokenType("Bearer")
                .build();
        when(authService.guestLogin(any(GuestLoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").value("guest-access-token"));

        verify(authService).guestLogin(any(GuestLoginRequest.class));
    }
}
