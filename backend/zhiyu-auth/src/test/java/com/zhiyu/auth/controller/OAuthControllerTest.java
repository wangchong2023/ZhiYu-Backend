package com.zhiyu.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.auth.dto.LoginResponse;
import com.zhiyu.auth.dto.OAuthLoginRequest;
import com.zhiyu.auth.service.OAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OAuthControllerTest {

    @Mock
    private OAuthService oauthService;

    @InjectMocks
    private OAuthController oauthController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(oauthController).build();
        objectMapper = new ObjectMapper();
    }

    // ── WeChat Login ───────────────────────────────────────────

    @Test
    void shouldLoginViaWechatWhenValidCode() throws Exception {
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setCode("wx-auth-code-123");
        request.setState("csrf-state");

        LoginResponse response = LoginResponse.builder()
                .accessToken("access-token-wx")
                .refreshToken("refresh-token-wx")
                .expiresIn(900)
                .tokenType("Bearer")
                .totpRequired(false)
                .isNewUser(true)
                .build();
        when(oauthService.login(eq("wechat"), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/oauth/wechat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token-wx"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token-wx"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.totpRequired").value(false))
                .andExpect(jsonPath("$.data.isNewUser").value(true))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNumber());

        verify(oauthService).login(eq("wechat"), any());
    }

    // ── Apple Login ────────────────────────────────────────────

    @Test
    void shouldLoginViaAppleWhenValidIdToken() throws Exception {
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setCode("apple-auth-code");
        request.setIdToken("apple-id-token");

        LoginResponse response = LoginResponse.builder()
                .accessToken("access-token-apple")
                .refreshToken("refresh-token-apple")
                .expiresIn(900)
                .tokenType("Bearer")
                .totpRequired(false)
                .isNewUser(false)
                .build();
        when(oauthService.login(eq("apple"), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/oauth/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token-apple"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token-apple"))
                .andExpect(jsonPath("$.data.isNewUser").value(false))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNumber());

        verify(oauthService).login(eq("apple"), any());
    }

    // ── Google Login ───────────────────────────────────────────

    @Test
    void shouldLoginViaGoogleWhenValidCode() throws Exception {
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setCode("google-auth-code");
        request.setState("google-state");

        LoginResponse response = LoginResponse.builder()
                .accessToken("access-token-google")
                .refreshToken("refresh-token-google")
                .expiresIn(900)
                .tokenType("Bearer")
                .totpRequired(false)
                .build();
        when(oauthService.login(eq("google"), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/oauth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token-google"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token-google"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNumber());

        verify(oauthService).login(eq("google"), any());
    }

    // ── Validation ─────────────────────────────────────────────

    @Test
    void shouldReturn400WhenOAuthRequestHasBlankCode() throws Exception {
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setCode("");

        mockMvc.perform(post("/api/v1/auth/oauth/wechat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenOAuthRequestBodyIsEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/auth/oauth/wechat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
