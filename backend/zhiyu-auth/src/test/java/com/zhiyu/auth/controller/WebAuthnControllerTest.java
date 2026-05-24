package com.zhiyu.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yubico.webauthn.AssertionResult;
import com.zhiyu.auth.dto.LoginResponse;
import com.zhiyu.auth.dto.WebAuthnRequest;
import com.zhiyu.auth.dto.WebAuthnResponse;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.entity.AuthUserLog;
import com.zhiyu.ufp.auth.jwt.JwtService;
import com.zhiyu.ufp.auth.jwt.JwtService.JwtPair;
import com.zhiyu.ufp.auth.mapper.AuthUserLogMapper;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.auth.webauthn.WebAuthnService;
import com.zhiyu.ufp.auth.webauthn.WebAuthnStartResult;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WebAuthnControllerTest {

    @Mock
    private WebAuthnService webAuthnService;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthUserMapper authUserMapper;

    @Mock
    private AuthUserLogMapper authUserLogMapper;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private WebAuthnController webAuthnController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(webAuthnController).build();
        objectMapper = new ObjectMapper();

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── Register Begin ─────────────────────────────────────────

    @Test
    void shouldBeginRegistrationWhenAuthenticated() throws Exception {
        when(authentication.getName()).thenReturn("testuser");

        AuthUser user = AuthUser.builder()
                .authUserId(1001L).authUserUsername("testuser").build();
        when(authUserMapper.selectOne(any())).thenReturn(user);

        WebAuthnStartResult startResult = new WebAuthnStartResult(
                "challenge-abc", "{\"rp\":{\"name\":\"ZhiYu\"}}");
        when(webAuthnService.startRegistration(1001L)).thenReturn(startResult);

        mockMvc.perform(post("/api/v1/auth/webauthn/register/begin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.challengeId").value("challenge-abc"))
                .andExpect(jsonPath("$.data.optionsJson").value("{\"rp\":{\"name\":\"ZhiYu\"}}"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNumber());

        verify(webAuthnService).startRegistration(1001L);
    }

    // ── Register Finish ────────────────────────────────────────

    @Test
    void shouldFinishRegistrationWhenValidRequest() throws Exception {
        WebAuthnRequest request = new WebAuthnRequest();
        request.setChallengeId("challenge-xyz");
        request.setCredentialJson("{\"type\":\"public-key\",\"id\":\"cred-1\"}");

        mockMvc.perform(post("/api/v1/auth/webauthn/register/finish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNumber());

        verify(webAuthnService).finishRegistration("challenge-xyz",
                "{\"type\":\"public-key\",\"id\":\"cred-1\"}");
    }

    @Test
    void shouldReturn400WhenRegisterFinishRequestHasBlankChallengeId() throws Exception {
        WebAuthnRequest request = new WebAuthnRequest();
        request.setChallengeId("");
        request.setCredentialJson("{\"type\":\"public-key\"}");

        mockMvc.perform(post("/api/v1/auth/webauthn/register/finish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenRegisterFinishRequestHasBlankCredentialJson() throws Exception {
        WebAuthnRequest request = new WebAuthnRequest();
        request.setChallengeId("challenge-1");
        request.setCredentialJson("");

        mockMvc.perform(post("/api/v1/auth/webauthn/register/finish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── Authenticate Begin ─────────────────────────────────────

    @Test
    void shouldBeginAuthenticationWhenValidUsername() throws Exception {
        WebAuthnRequest request = new WebAuthnRequest();
        request.setUsername("testuser");
        request.setChallengeId("ignored");
        request.setCredentialJson("ignored");

        WebAuthnStartResult startResult = new WebAuthnStartResult(
                "challenge-auth-1", "{\"challenge\":\"...\"}");
        when(webAuthnService.startAuthentication("testuser")).thenReturn(startResult);

        mockMvc.perform(post("/api/v1/auth/webauthn/authenticate/begin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.challengeId").value("challenge-auth-1"))
                .andExpect(jsonPath("$.data.optionsJson").value("{\"challenge\":\"...\"}"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNumber());

        verify(webAuthnService).startAuthentication("testuser");
    }

    // ── Authenticate Finish ────────────────────────────────────

    @Test
    void shouldFinishAuthenticationWhenValidAssertion() throws Exception {
        WebAuthnRequest request = new WebAuthnRequest();
        request.setChallengeId("challenge-finish-1");
        request.setCredentialJson("{\"type\":\"public-key\",\"response\":{}}");

        AssertionResult mockAssertionResult = mock(AssertionResult.class);
        when(mockAssertionResult.getUsername()).thenReturn("testuser");
        when(webAuthnService.finishAuthentication(
                eq("challenge-finish-1"), eq("{\"type\":\"public-key\",\"response\":{}}")))
                .thenReturn(mockAssertionResult);

        AuthUser user = AuthUser.builder()
                .authUserId(1001L).authUserUsername("testuser")
                .authUserScope("FULL").build();
        when(authUserMapper.selectOne(any())).thenReturn(user);

        when(jwtService.issue(eq(1001L), eq("testuser"), eq("FULL")))
                .thenReturn(new JwtPair("access-token", "refresh-token", 900));

        mockMvc.perform(post("/api/v1/auth/webauthn/authenticate/finish")
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

        verify(authUserLogMapper).insert(any(AuthUserLog.class));
    }

    @Test
    void shouldFinishAuthenticationWhenUserScopeIsNull() throws Exception {
        WebAuthnRequest request = new WebAuthnRequest();
        request.setChallengeId("ch-2");
        request.setCredentialJson("{}");

        AssertionResult mockAssertionResult = mock(AssertionResult.class);
        when(mockAssertionResult.getUsername()).thenReturn("scopeless");
        when(webAuthnService.finishAuthentication(eq("ch-2"), eq("{}")))
                .thenReturn(mockAssertionResult);

        AuthUser user = AuthUser.builder()
                .authUserId(2001L).authUserUsername("scopeless")
                .authUserScope(null).build();
        when(authUserMapper.selectOne(any())).thenReturn(user);

        when(jwtService.issue(eq(2001L), eq("scopeless"), eq("FULL")))
                .thenReturn(new JwtPair("at", "rt", 900));

        mockMvc.perform(post("/api/v1/auth/webauthn/authenticate/finish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").value("at"))
                .andExpect(jsonPath("$.data.refreshToken").value("rt"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        verify(jwtService).issue(eq(2001L), eq("scopeless"), eq("FULL"));
        verify(authUserLogMapper).insert(any(AuthUserLog.class));
    }

    @Test
    void shouldReturn400WhenAuthenticateFinishRequestHasBlankChallengeId() throws Exception {
        WebAuthnRequest request = new WebAuthnRequest();
        request.setChallengeId("");
        request.setCredentialJson("{}");

        mockMvc.perform(post("/api/v1/auth/webauthn/authenticate/finish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── Error Paths ────────────────────────────────────────────

    @Test
    void shouldThrowBizExceptionWhenUserNotFoundInRegisterBegin() throws Exception {
        when(authentication.getName()).thenReturn("unknownuser");
        when(authUserMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> webAuthnController.registerBegin())
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    void shouldThrowBizExceptionWhenUserNotFoundInAuthenticateFinish() throws Exception {
        WebAuthnRequest request = new WebAuthnRequest();
        request.setChallengeId("ch-unknown");
        request.setCredentialJson("{}");

        AssertionResult mockAssertionResult = mock(AssertionResult.class);
        when(mockAssertionResult.getUsername()).thenReturn("unknown");
        when(webAuthnService.finishAuthentication(eq("ch-unknown"), eq("{}")))
                .thenReturn(mockAssertionResult);
        when(authUserMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> webAuthnController.authenticateFinish(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    void shouldReturn400WhenAuthenticateBeginRequestBodyIsEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/auth/webauthn/authenticate/begin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
