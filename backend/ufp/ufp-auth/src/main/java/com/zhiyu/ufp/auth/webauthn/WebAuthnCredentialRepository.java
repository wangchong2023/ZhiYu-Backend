package com.zhiyu.ufp.auth.webauthn;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.exception.Base64UrlException;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.entity.AuthUserWebAuthn;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.auth.mapper.AuthUserWebAuthnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class WebAuthnCredentialRepository implements CredentialRepository {

    private final AuthUserWebAuthnMapper webAuthnMapper;
    private final AuthUserMapper authUserMapper;

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(final String username) {
        AuthUser user = authUserMapper.selectOne(
                new LambdaQueryWrapper<AuthUser>()
                        .eq(AuthUser::getAuthUserUsername, username));
        if (user == null) {
            return Set.of();
        }
        List<AuthUserWebAuthn> credentials = webAuthnMapper.selectList(
                new LambdaQueryWrapper<AuthUserWebAuthn>()
                        .eq(AuthUserWebAuthn::getAuthUserId, user.getAuthUserId())
                        .eq(AuthUserWebAuthn::getEnabled, AuthUserWebAuthn.ENABLED));
        return credentials.stream()
                .map(c -> {
                    try {
                        return PublicKeyCredentialDescriptor.builder()
                                .id(ByteArray.fromBase64Url(c.getCredentialId()))
                                .build();
                    } catch (Base64UrlException e) {
                        throw new RuntimeException("Invalid credential ID format", e);
                    }
                })
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(final String username) {
        AuthUser user = authUserMapper.selectOne(
                new LambdaQueryWrapper<AuthUser>()
                        .eq(AuthUser::getAuthUserUsername, username));
        if (user == null) {
            return Optional.empty();
        }
        byte[] bytes = String.valueOf(user.getAuthUserId()).getBytes(StandardCharsets.UTF_8);
        return Optional.of(new ByteArray(bytes));
    }

    @Override
    public Optional<String> getUsernameForUserHandle(final ByteArray userHandle) {
        try {
            Long userId = Long.parseLong(new String(userHandle.getBytes(), StandardCharsets.UTF_8));
            AuthUser user = authUserMapper.selectById(userId);
            return user != null ? Optional.of(user.getAuthUserUsername()) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<RegisteredCredential> lookup(final ByteArray credentialId, final ByteArray userHandle) {
        String credIdBase64 = credentialId.getBase64Url();
        AuthUserWebAuthn credential = webAuthnMapper.selectOne(
                new LambdaQueryWrapper<AuthUserWebAuthn>()
                        .eq(AuthUserWebAuthn::getCredentialId, credIdBase64));
        if (credential == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(RegisteredCredential.builder()
                    .credentialId(ByteArray.fromBase64Url(credential.getCredentialId()))
                    .userHandle(new ByteArray(String.valueOf(credential.getAuthUserId()).getBytes(StandardCharsets.UTF_8)))
                    .publicKeyCose(ByteArray.fromBase64(credential.getPublicKey()))
                    .signatureCount(credential.getSignCount() != null ? credential.getSignCount() : 0L)
                    .build());
        } catch (Base64UrlException e) {
            throw new RuntimeException("Invalid credential ID format", e);
        }
    }

    @Override
    public Set<RegisteredCredential> lookupAll(final ByteArray credentialId) {
        String credIdBase64 = credentialId.getBase64Url();
        List<AuthUserWebAuthn> credentials = webAuthnMapper.selectList(
                new LambdaQueryWrapper<AuthUserWebAuthn>()
                        .eq(AuthUserWebAuthn::getCredentialId, credIdBase64));
        return credentials.stream()
                .map(c -> {
                    try {
                        return RegisteredCredential.builder()
                                .credentialId(ByteArray.fromBase64Url(c.getCredentialId()))
                                .userHandle(new ByteArray(String.valueOf(c.getAuthUserId()).getBytes(StandardCharsets.UTF_8)))
                                .publicKeyCose(ByteArray.fromBase64(c.getPublicKey()))
                                .signatureCount(c.getSignCount() != null ? c.getSignCount() : 0L)
                                .build();
                    } catch (Base64UrlException e) {
                        throw new RuntimeException("Invalid credential ID format", e);
                    }
                })
                .collect(Collectors.toSet());
    }
}
