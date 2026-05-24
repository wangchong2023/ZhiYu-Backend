package com.zhiyu.auth.controller;

import com.zhiyu.auth.dto.CaptchaResponse;
import com.zhiyu.auth.service.CaptchaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CaptchaControllerTest {

    @Mock
    private CaptchaService captchaService;

    @InjectMocks
    private CaptchaController captchaController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(captchaController).build();
    }

    // ── Get Captcha Image ──────────────────────────────────────

    @Test
    void shouldReturnCaptchaWhenDefaultScene() throws Exception {
        CaptchaResponse response = CaptchaResponse.builder()
                .captchaToken("captcha-token-123")
                .captchaImage("data:image/png;base64,iVBORw0KGgo==")
                .build();
        when(captchaService.generate("zhiyu_login")).thenReturn(response);

        mockMvc.perform(get("/api/v1/auth/captcha/image"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.captchaToken").value("captcha-token-123"))
                .andExpect(jsonPath("$.data.captchaImage").value("data:image/png;base64,iVBORw0KGgo=="))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNumber());

        verify(captchaService).generate("zhiyu_login");
    }

    @Test
    void shouldReturnCaptchaWhenCustomScene() throws Exception {
        CaptchaResponse response = CaptchaResponse.builder()
                .captchaToken("custom-token")
                .captchaImage("data:image/png;base64,abcd=")
                .build();
        when(captchaService.generate("register_scene")).thenReturn(response);

        mockMvc.perform(get("/api/v1/auth/captcha/image")
                        .param("sceneId", "register_scene"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.captchaToken").value("custom-token"))
                .andExpect(jsonPath("$.data.captchaImage").value("data:image/png;base64,abcd="))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNumber());

        verify(captchaService).generate("register_scene");
    }

    @Test
    void shouldPassSceneIdToServiceWhenSpecialCharactersInScene() throws Exception {
        String specialScene = "login_v2@special";
        CaptchaResponse response = CaptchaResponse.builder()
                .captchaToken("special-token")
                .captchaImage("data:image/png;base64,special=")
                .build();
        when(captchaService.generate(specialScene)).thenReturn(response);

        mockMvc.perform(get("/api/v1/auth/captcha/image")
                        .param("sceneId", specialScene))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.captchaToken").value("special-token"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNumber());

        verify(captchaService).generate(specialScene);
    }
}
