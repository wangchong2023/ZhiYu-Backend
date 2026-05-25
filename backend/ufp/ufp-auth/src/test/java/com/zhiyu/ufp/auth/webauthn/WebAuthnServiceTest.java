package com.zhiyu.ufp.auth.webauthn;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.AuthenticatorAssertionResponse;
import com.yubico.webauthn.data.AuthenticatorAttestationResponse;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.ClientAssertionExtensionOutputs;
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.entity.AuthUserWebAuthn;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.auth.mapper.AuthUserWebAuthnMapper;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebAuthnServiceTest {

    @Mock
    private RelyingPartyIdentity rpIdentity;

    @Mock
    private WebAuthnCredentialRepository credentialRepository;

    @Mock
    private AuthUserWebAuthnMapper webAuthnMapper;

    @Mock
    private AuthUserMapper authUserMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    private ObjectMapper objectMapper = new ObjectMapper();

    private WebAuthnService service;
    private RelyingParty mockRelyingParty;
    private ValueOperations<String, String> mockValueOps;

    private MockedConstruction<RelyingParty> mockedRpConstruction;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        mockedRpConstruction = mockConstruction(RelyingParty.class);
        service = new WebAuthnService(rpIdentity, credentialRepository,
                webAuthnMapper, authUserMapper, redisTemplate, objectMapper);
        mockRelyingParty = mockedRpConstruction.constructed().get(0);

        mockValueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(mockValueOps);
    }

    @AfterEach
    void tearDown() {
        mockedRpConstruction.close();
    }

    // ── helper ───────────────────────────────────────────────────

    private AuthUser buildUser(Long id, String username, String nick) {
        return AuthUser.builder()
                .authUserId(id)
                .authUserUsername(username)
                .authUserNick(nick)
                .authUserEnable(1)
                .build();
    }

    // ── startRegistration() ──────────────────────────────────────

    @Test
    void shouldThrowBizExceptionWhenUserNotFoundForStartRegistration() {
        Long userId = 9999L;
        when(authUserMapper.selectById(userId)).thenReturn(null);

        assertThatThrownBy(() -> service.startRegistration(userId))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.RESOURCE_NOT_FOUND.getCode());
    }

    @Test
    void shouldStartRegistrationSuccessfully() throws Exception {
        Long userId = 1001L;
        AuthUser user = buildUser(userId, "zhangsan", "张三");
        when(authUserMapper.selectById(userId)).thenReturn(user);

        PublicKeyCredentialCreationOptions mockOptions =
                mock(PublicKeyCredentialCreationOptions.class);
        when(mockOptions.toJson()).thenReturn("{\"challenge\":\"test-json\"}");
        when(mockOptions.toCredentialsCreateJson()).thenReturn("{\"pkcco\":\"create-json\"}");
        when(mockRelyingParty.startRegistration(any())).thenReturn(mockOptions);

        WebAuthnStartResult result = service.startRegistration(userId);

        assertThat(result).isNotNull();
        assertThat(result.challengeId()).isNotBlank();
        assertThat(result.optionsJson()).isEqualTo("{\"pkcco\":\"create-json\"}");

        // Verify redis set call
        verify(mockValueOps).set(
                anyString(),
                eq("{\"challenge\":\"test-json\"}"),
                eq(Duration.ofMinutes(5)));
        verify(authUserMapper).selectById(userId);
    }

    // ── finishRegistration() ─────────────────────────────────────

    @Test
    void shouldThrowActionExpiredWhenChallengeNotFoundForFinishRegistration() {
        String challengeId = "expired-challenge";
        when(mockValueOps.get(anyString())).thenReturn(null);

        assertThatThrownBy(() -> service.finishRegistration(challengeId, "{}"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.ACTION_EXPIRED.getCode());
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldFinishRegistrationSuccessfully() throws Exception {
        String challengeId = "valid-challenge";
        String storedJson = "{\"challenge\":\"stored-challenge\"}";
        String credentialJson = "{\"id\":\"cred-1\"}";

        when(mockValueOps.get(anyString())).thenReturn(storedJson);

        // Mock PublicKeyCredentialCreationOptions.fromJson (static)
        PublicKeyCredentialCreationOptions mockOptions =
                mock(PublicKeyCredentialCreationOptions.class);
        UserIdentity mockUserIdentity = mock(UserIdentity.class);
        ByteArray mockUserIdByteArray = mock(ByteArray.class);
        when(mockUserIdByteArray.getBytes()).thenReturn("1001".getBytes());
        when(mockUserIdentity.getId()).thenReturn(mockUserIdByteArray);
        when(mockOptions.getUser()).thenReturn(mockUserIdentity);

        // Mock PublicKeyCredential.parseRegistrationResponseJson (static)
        PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs>
                mockCredential = mock(PublicKeyCredential.class);

        // Mock RegistrationResult
        RegistrationResult mockResult = mock(RegistrationResult.class);
        PublicKeyCredentialDescriptor mockKeyId = mock(PublicKeyCredentialDescriptor.class);
        ByteArray mockCredIdBytes = mock(ByteArray.class);
        ByteArray mockPublicKeyCose = mock(ByteArray.class);
        when(mockResult.getKeyId()).thenReturn(mockKeyId);
        when(mockKeyId.getId()).thenReturn(mockCredIdBytes);
        when(mockCredIdBytes.getBase64Url()).thenReturn("credentialIdBase64");
        when(mockResult.getPublicKeyCose()).thenReturn(mockPublicKeyCose);
        when(mockPublicKeyCose.getBase64()).thenReturn("publicKeyBase64");
        when(mockResult.getSignatureCount()).thenReturn(0L);

        when(mockRelyingParty.finishRegistration(any())).thenReturn(mockResult);

        try (MockedStatic<PublicKeyCredentialCreationOptions> mockedOptions =
                     mockStatic(PublicKeyCredentialCreationOptions.class);
             MockedStatic<PublicKeyCredential> mockedCred =
                     mockStatic(PublicKeyCredential.class)) {

            mockedOptions.when(() -> PublicKeyCredentialCreationOptions.fromJson(storedJson))
                    .thenReturn(mockOptions);
            mockedCred.when(() -> PublicKeyCredential.parseRegistrationResponseJson(credentialJson))
                    .thenReturn(mockCredential);

            service.finishRegistration(challengeId, credentialJson);
        }

        // Verify redis operations
        verify(mockValueOps).get(anyString());
        verify(redisTemplate).delete(anyString());

        // Verify mapper insert
        ArgumentCaptor<AuthUserWebAuthn> captor =
                ArgumentCaptor.forClass(AuthUserWebAuthn.class);
        verify(webAuthnMapper).insert(captor.capture());

        AuthUserWebAuthn inserted = captor.getValue();
        assertThat(inserted.getAuthUserId()).isEqualTo(1001L);
        assertThat(inserted.getCredentialId()).isEqualTo("credentialIdBase64");
        assertThat(inserted.getPublicKey()).isEqualTo("publicKeyBase64");
        assertThat(inserted.getSignCount()).isZero();
        assertThat(inserted.getEnabled()).isEqualTo(1);
        assertThat(inserted.getCreatedTime()).isNotNull();
    }

    @Test
    void shouldThrowRegistrationFailedWhenYubicoThrows() throws Exception {
        String challengeId = "valid-challenge";
        String storedJson = "{\"challenge\":\"stored\"}";
        String credentialJson = "{\"id\":\"bad\"}";

        when(mockValueOps.get(anyString())).thenReturn(storedJson);

        PublicKeyCredentialCreationOptions mockOptions =
                mock(PublicKeyCredentialCreationOptions.class);

        try (MockedStatic<PublicKeyCredentialCreationOptions> mockedOptions =
                     mockStatic(PublicKeyCredentialCreationOptions.class);
             MockedStatic<PublicKeyCredential> mockedCred =
                     mockStatic(PublicKeyCredential.class)) {

            mockedOptions.when(() -> PublicKeyCredentialCreationOptions.fromJson(storedJson))
                    .thenReturn(mockOptions);

            PublicKeyCredential mockCredential = mock(PublicKeyCredential.class);
            mockedCred.when(() -> PublicKeyCredential.parseRegistrationResponseJson(credentialJson))
                    .thenReturn(mockCredential);

            when(mockRelyingParty.finishRegistration(any()))
                    .thenThrow(new RegistrationFailedException(
                            new IllegalArgumentException("Verification failed")));

            assertThatThrownBy(() -> service.finishRegistration(challengeId, credentialJson))
                    .isInstanceOf(RegistrationFailedException.class);
        }
    }

    // ── startAuthentication() ────────────────────────────────────

    @Test
    void shouldStartAuthenticationSuccessfully() throws Exception {
        String username = "zhangsan";

        AssertionRequest mockAssertionRequest = mock(AssertionRequest.class);
        when(mockAssertionRequest.toJson()).thenReturn("{\"assertion\":\"test-json\"}");
        when(mockAssertionRequest.toCredentialsGetJson())
                .thenReturn("{\"credGet\":\"get-json\"}");
        when(mockRelyingParty.startAssertion(any())).thenReturn(mockAssertionRequest);

        WebAuthnStartResult result = service.startAuthentication(username);

        assertThat(result).isNotNull();
        assertThat(result.challengeId()).isNotBlank();
        assertThat(result.optionsJson()).isEqualTo("{\"credGet\":\"get-json\"}");

        verify(mockValueOps).set(
                anyString(),
                eq("{\"assertion\":\"test-json\"}"),
                eq(Duration.ofMinutes(5)));
    }

    // ── finishAuthentication() ───────────────────────────────────

    @Test
    void shouldThrowActionExpiredWhenChallengeNotFoundForFinishAuthentication() {
        String challengeId = "expired-challenge";
        when(mockValueOps.get(anyString())).thenReturn(null);

        assertThatThrownBy(() -> service.finishAuthentication(challengeId, "{}"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.ACTION_EXPIRED.getCode());
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldFinishAuthenticationSuccessfully() throws Exception {
        String challengeId = "valid-challenge";
        String storedJson = "{\"assertion\":\"stored\"}";
        String credentialJson = "{\"id\":\"cred-1\"}";

        when(mockValueOps.get(anyString())).thenReturn(storedJson);

        AssertionRequest mockRequest = mock(AssertionRequest.class);
        AssertionResult mockResult = mock(AssertionResult.class);
        ByteArray mockCredId = mock(ByteArray.class);
        when(mockResult.isSuccess()).thenReturn(true);
        when(mockResult.getCredentialId()).thenReturn(mockCredId);
        when(mockCredId.getBase64Url()).thenReturn("credIdBase64");
        when(mockResult.getSignatureCount()).thenReturn(5L);

        PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs>
                mockCredential = mock(PublicKeyCredential.class);

        when(mockRelyingParty.finishAssertion(any())).thenReturn(mockResult);

        // Mock sign count update query
        AuthUserWebAuthn existingCred = AuthUserWebAuthn.builder()
                .authUserWebAuthnId(1L)
                .authUserId(1001L)
                .credentialId("credIdBase64")
                .signCount(0)
                .build();
        when(webAuthnMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingCred);

        try (MockedStatic<AssertionRequest> mockedRequest =
                     mockStatic(AssertionRequest.class);
             MockedStatic<PublicKeyCredential> mockedCred =
                     mockStatic(PublicKeyCredential.class)) {

            mockedRequest.when(() -> AssertionRequest.fromJson(storedJson))
                    .thenReturn(mockRequest);
            mockedCred.when(() -> PublicKeyCredential.parseAssertionResponseJson(credentialJson))
                    .thenReturn(mockCredential);

            AssertionResult result = service.finishAuthentication(challengeId, credentialJson);

            assertThat(result).isEqualTo(mockResult);
        }

        // Verify redis operations
        verify(mockValueOps).get(anyString());
        verify(redisTemplate).delete(anyString());

        // Verify sign count update
        assertThat(existingCred.getSignCount()).isEqualTo(5);
        assertThat(existingCred.getLastUsedTime()).isNotNull();
        verify(webAuthnMapper).updateById(existingCred);
    }

    @Test
    void shouldThrowWebAuthnFailedWhenAssertionResultNotSuccess() throws Exception {
        String challengeId = "valid-challenge";
        String storedJson = "{\"assertion\":\"stored\"}";
        String credentialJson = "{\"id\":\"cred-1\"}";

        when(mockValueOps.get(anyString())).thenReturn(storedJson);

        AssertionRequest mockRequest = mock(AssertionRequest.class);
        AssertionResult mockResult = mock(AssertionResult.class);
        when(mockResult.isSuccess()).thenReturn(false);

        PublicKeyCredential mockCredential = mock(PublicKeyCredential.class);
        when(mockRelyingParty.finishAssertion(any())).thenReturn(mockResult);

        try (MockedStatic<AssertionRequest> mockedRequest =
                     mockStatic(AssertionRequest.class);
             MockedStatic<PublicKeyCredential> mockedCred =
                     mockStatic(PublicKeyCredential.class)) {

            mockedRequest.when(() -> AssertionRequest.fromJson(storedJson))
                    .thenReturn(mockRequest);
            mockedCred.when(() -> PublicKeyCredential.parseAssertionResponseJson(credentialJson))
                    .thenReturn(mockCredential);

            assertThatThrownBy(() -> service.finishAuthentication(challengeId, credentialJson))
                    .isInstanceOf(BizException.class)
                    .extracting(ex -> ((BizException) ex).getCode())
                    .isEqualTo(BizErrorCode.WEBAUTHN_FAILED.getCode());
        }
    }

    @Test
    void shouldThrowAssertionFailedWhenYubicoThrows() throws Exception {
        String challengeId = "valid-challenge";
        String storedJson = "{\"assertion\":\"stored\"}";
        String credentialJson = "{\"id\":\"bad\"}";

        when(mockValueOps.get(anyString())).thenReturn(storedJson);

        AssertionRequest mockRequest = mock(AssertionRequest.class);

        when(mockRelyingParty.finishAssertion(any()))
                .thenThrow(new AssertionFailedException("Assertion failed"));

        try (MockedStatic<AssertionRequest> mockedRequest =
                     mockStatic(AssertionRequest.class);
             MockedStatic<PublicKeyCredential> mockedCred =
                     mockStatic(PublicKeyCredential.class)) {

            mockedRequest.when(() -> AssertionRequest.fromJson(storedJson))
                    .thenReturn(mockRequest);
            PublicKeyCredential mockCredential = mock(PublicKeyCredential.class);
            mockedCred.when(() -> PublicKeyCredential.parseAssertionResponseJson(credentialJson))
                    .thenReturn(mockCredential);

            assertThatThrownBy(() -> service.finishAuthentication(challengeId, credentialJson))
                    .isInstanceOf(AssertionFailedException.class);
        }
    }

    // ── updateSignCount edge case ────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void shouldNotUpdateSignCountWhenCredentialNotFound() throws Exception {
        String challengeId = "valid-challenge";
        String storedJson = "{\"assertion\":\"stored\"}";
        String credentialJson = "{\"id\":\"cred-unknown\"}";

        when(mockValueOps.get(anyString())).thenReturn(storedJson);

        AssertionRequest mockRequest = mock(AssertionRequest.class);
        AssertionResult mockResult = mock(AssertionResult.class);
        ByteArray mockCredId = mock(ByteArray.class);
        when(mockResult.isSuccess()).thenReturn(true);
        when(mockResult.getCredentialId()).thenReturn(mockCredId);
        when(mockCredId.getBase64Url()).thenReturn("unknownCredId");
        when(mockResult.getSignatureCount()).thenReturn(3L);

        PublicKeyCredential mockCredential = mock(PublicKeyCredential.class);
        when(mockRelyingParty.finishAssertion(any())).thenReturn(mockResult);

        // Credential not found in DB
        when(webAuthnMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        try (MockedStatic<AssertionRequest> mockedRequest =
                     mockStatic(AssertionRequest.class);
             MockedStatic<PublicKeyCredential> mockedCred =
                     mockStatic(PublicKeyCredential.class)) {

            mockedRequest.when(() -> AssertionRequest.fromJson(storedJson))
                    .thenReturn(mockRequest);
            mockedCred.when(() -> PublicKeyCredential.parseAssertionResponseJson(credentialJson))
                    .thenReturn(mockCredential);

            service.finishAuthentication(challengeId, credentialJson);
        }

        // Should not attempt update when credential not found
        verify(webAuthnMapper, never()).updateById(any(AuthUserWebAuthn.class));
    }
}
