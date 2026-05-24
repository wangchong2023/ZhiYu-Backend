package com.zhiyu.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.auth.dto.LoginResponse;
import com.zhiyu.auth.dto.TotpEnableRequest;
import com.zhiyu.auth.dto.TotpSetupResponse;
import com.zhiyu.auth.dto.TotpVerifyRequest;
import com.zhiyu.auth.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TotpControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private TotpController totpController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(totpController).build();
        objectMapper = new ObjectMapper();

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(authentication.getPrincipal()).thenReturn("1001");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── Setup ──────────────────────────────────────────────────

    @Test
    void shouldSetupTotpWhenAuthenticated() throws Exception {
        TotpSetupResponse response = TotpSetupResponse.builder()
                .secret("BASE32SECRET")
                .qrUri("otpauth://totp/ZhiYu:testuser?secret=BASE32SECRET&issuer=ZhiYu")
                .build();
        when(authService.setupTotp(1001L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/totp/setup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.secret").value("BASE32SECRET"))
                .andExpect(jsonPath("$.data.qrUri").isNotEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNumber());

        verify(authService).setupTotp(1001L);
    }

    // ── Enable ─────────────────────────────────────────────────

    @Test
    void shouldEnableTotpWhenValidCode() throws Exception {
        TotpEnableRequest request = new TotpEnableRequest();
        request.setCode("123456");

        mockMvc.perform(post("/api/v1/auth/totp/enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNumber());

        verify(authService).enableTotp(1001L, "123456");
    }

    @Test
    void shouldReturn400WhenEnableRequestHasBlankCode() throws Exception {
        TotpEnableRequest request = new TotpEnableRequest();
        request.setCode("");

        mockMvc.perform(post("/api/v1/auth/totp/enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenEnableRequestBodyIsEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/auth/totp/enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── Disable ────────────────────────────────────────────────

    @Test
    void shouldDisableTotpWhenAuthenticated() throws Exception {
        mockMvc.perform(delete("/api/v1/auth/totp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNumber());

        verify(authService).disableTotp(1001L);
    }

    // ── Verify ─────────────────────────────────────────────────

    @Test
    void shouldVerifyTotpWhenValidCode() throws Exception {
        TotpVerifyRequest request = new TotpVerifyRequest();
        request.setCode("654321");

        LoginResponse response = LoginResponse.builder()
                .accessToken("full-access")
                .refreshToken("full-refresh")
                .expiresIn(900)
                .tokenType("Bearer")
                .totpRequired(false)
                .build();
        when(authService.verifyTotpLogin(1001L, "654321")).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/totp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.accessToken").value("full-access"))
                .andExpect(jsonPath("$.data.refreshToken").value("full-refresh"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.totpRequired").value(false))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNumber());

        verify(authService).verifyTotpLogin(1001L, "654321");
    }

    @Test
    void shouldReturn400WhenVerifyRequestHasBlankCode() throws Exception {
        TotpVerifyRequest request = new TotpVerifyRequest();
        request.setCode("");

        mockMvc.perform(post("/api/v1/auth/totp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenVerifyRequestBodyIsEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/auth/totp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
