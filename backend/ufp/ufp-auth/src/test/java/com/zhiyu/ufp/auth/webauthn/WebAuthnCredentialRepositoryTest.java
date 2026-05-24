package com.zhiyu.ufp.auth.webauthn;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.exception.Base64UrlException;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.entity.AuthUserWebAuthn;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.auth.mapper.AuthUserWebAuthnMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebAuthnCredentialRepositoryTest {

    @Mock
    private AuthUserWebAuthnMapper webAuthnMapper;

    @Mock
    private AuthUserMapper authUserMapper;

    @InjectMocks
    private WebAuthnCredentialRepository repository;

    private AuthUser testUser;

    @BeforeEach
    void setUp() {
        testUser = AuthUser.builder()
                .authUserId(1001L)
                .authUserUsername("testuser")
                .authUserNick("Test User")
                .build();
    }

    // ── getCredentialIdsForUsername() ──────────────────────────────

    @Test
    void shouldReturnEmptyWhenUserNotFound() {
        when(authUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        Set<PublicKeyCredentialDescriptor> result = repository.getCredentialIdsForUsername("unknown");

        assertThat(result).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnCredentialsForValidUser() {
        when(authUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);

        AuthUserWebAuthn cred = AuthUserWebAuthn.builder()
                .authUserId(1001L)
                .credentialId("dGVzdC1jcmVkZW50aWFsLWlk")
                .enabled(1)
                .build();
        when(webAuthnMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(cred));

        Set<PublicKeyCredentialDescriptor> result = repository.getCredentialIdsForUsername("testuser");

        assertThat(result).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldThrowRuntimeExceptionForInvalidCredentialId() {
        when(authUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);

        AuthUserWebAuthn cred = AuthUserWebAuthn.builder()
                .authUserId(1001L)
                .credentialId("!!!invalid-base64url!!!")
                .enabled(1)
                .build();
        when(webAuthnMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(cred));

        assertThatThrownBy(() -> repository.getCredentialIdsForUsername("testuser"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid credential ID");
    }

    // ── getUserHandleForUsername() ─────────────────────────────────

    @Test
    void shouldReturnEmptyOptionalWhenUserNotFound() {
        when(authUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        Optional<ByteArray> result = repository.getUserHandleForUsername("unknown");

        assertThat(result).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnUserHandleForValidUser() {
        when(authUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);

        Optional<ByteArray> result = repository.getUserHandleForUsername("testuser");

        assertThat(result).isPresent();
        assertThat(new String(result.get().getBytes())).isEqualTo("1001");
    }

    // ── getUsernameForUserHandle() ─────────────────────────────────

    @Test
    void shouldReturnEmptyWhenUserIdNotFound() {
        Long nonExistentId = 9999L;
        when(authUserMapper.selectById(nonExistentId)).thenReturn(null);

        ByteArray userHandle = new ByteArray(String.valueOf(nonExistentId).getBytes());
        Optional<String> result = repository.getUsernameForUserHandle(userHandle);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnUsernameWhenUserIdFound() {
        Long userId = 1001L;
        when(authUserMapper.selectById(userId)).thenReturn(testUser);

        ByteArray userHandle = new ByteArray(String.valueOf(userId).getBytes());
        Optional<String> result = repository.getUsernameForUserHandle(userHandle);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("testuser");
    }

    @Test
    void shouldReturnEmptyWhenUserHandleIsNotNumeric() {
        ByteArray userHandle = new ByteArray("not-a-number".getBytes());

        Optional<String> result = repository.getUsernameForUserHandle(userHandle);

        assertThat(result).isEmpty();
    }

    // ── lookup() ───────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnEmptyWhenCredentialNotFound() {
        String credIdBase64 = "dGVzdC1jcmVkZW50aWFsLWlk";
        ByteArray credentialId;
        try {
            credentialId = ByteArray.fromBase64Url(credIdBase64);
        } catch (Base64UrlException e) {
            throw new RuntimeException(e);
        }
        ByteArray userHandle = new ByteArray("1001".getBytes());

        when(webAuthnMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        Optional<com.yubico.webauthn.RegisteredCredential> result =
                repository.lookup(credentialId, userHandle);

        assertThat(result).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnRegisteredCredentialWhenFound() {
        String credIdBase64 = "dGVzdC1jcmVkZW50aWFsLWlk";
        String publicKeyBase64 = "dGVzdC1wdWJsaWMta2V5";
        ByteArray credentialId;
        try {
            credentialId = ByteArray.fromBase64Url(credIdBase64);
        } catch (Base64UrlException e) {
            throw new RuntimeException(e);
        }
        ByteArray userHandle = new ByteArray("1001".getBytes());

        AuthUserWebAuthn credential = AuthUserWebAuthn.builder()
                .authUserId(1001L)
                .credentialId(credIdBase64)
                .publicKey(publicKeyBase64)
                .signCount(5)
                .enabled(1)
                .build();
        when(webAuthnMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(credential);

        Optional<com.yubico.webauthn.RegisteredCredential> result =
                repository.lookup(credentialId, userHandle);

        assertThat(result).isPresent();
        assertThat(result.get().getSignatureCount()).isEqualTo(5L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDefaultSignCountToZeroWhenNull() {
        String credIdBase64 = "dGVzdC1jcmVkZW50aWFsLWlk";
        String publicKeyBase64 = "dGVzdC1wdWJsaWMta2V5";
        ByteArray credentialId;
        try {
            credentialId = ByteArray.fromBase64Url(credIdBase64);
        } catch (Base64UrlException e) {
            throw new RuntimeException(e);
        }
        ByteArray userHandle = new ByteArray("1001".getBytes());

        AuthUserWebAuthn credential = AuthUserWebAuthn.builder()
                .authUserId(1001L)
                .credentialId(credIdBase64)
                .publicKey(publicKeyBase64)
                .signCount(null)
                .enabled(1)
                .build();
        when(webAuthnMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(credential);

        Optional<com.yubico.webauthn.RegisteredCredential> result =
                repository.lookup(credentialId, userHandle);

        assertThat(result).isPresent();
        assertThat(result.get().getSignatureCount()).isZero();
    }

    // ── lookupAll() ────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnEmptySetWhenNoCredentialsFound() {
        String credIdBase64 = "dGVzdC1jcmVkZW50aWFsLWlk";
        ByteArray credentialId;
        try {
            credentialId = ByteArray.fromBase64Url(credIdBase64);
        } catch (Base64UrlException e) {
            throw new RuntimeException(e);
        }

        when(webAuthnMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        Set<com.yubico.webauthn.RegisteredCredential> result = repository.lookupAll(credentialId);

        assertThat(result).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnCredentialsWhenFound() {
        String credIdBase64 = "dGVzdC1jcmVkZW50aWFsLWlk";
        String publicKeyBase64 = "dGVzdC1wdWJsaWMta2V5";
        ByteArray credentialId;
        try {
            credentialId = ByteArray.fromBase64Url(credIdBase64);
        } catch (Base64UrlException e) {
            throw new RuntimeException(e);
        }

        AuthUserWebAuthn cred = AuthUserWebAuthn.builder()
                .authUserId(1001L)
                .credentialId(credIdBase64)
                .publicKey(publicKeyBase64)
                .signCount(3)
                .enabled(1)
                .build();
        when(webAuthnMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(cred));

        Set<com.yubico.webauthn.RegisteredCredential> result = repository.lookupAll(credentialId);

        assertThat(result).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldThrowRuntimeExceptionForInvalidCredentialIdInLookupAll() {
        String invalidCredId = "!!!invalid-base64url!!!";
        ByteArray credentialId;
        try {
            credentialId = ByteArray.fromBase64Url("dGVzdC1jcmVkZW50aWFsLWlk");
        } catch (Base64UrlException e) {
            throw new RuntimeException(e);
        }

        AuthUserWebAuthn cred = AuthUserWebAuthn.builder()
                .authUserId(1001L)
                .credentialId(invalidCredId)
                .publicKey("dGVzdC1wdWJsaWMta2V5")
                .signCount(1)
                .enabled(1)
                .build();
        when(webAuthnMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(cred));

        assertThatThrownBy(() -> repository.lookupAll(credentialId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid credential ID");
    }
}
