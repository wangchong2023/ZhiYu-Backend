package com.zhiyu.ufp.auth.webauthn;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorAssertionResponse;
import com.yubico.webauthn.data.AuthenticatorAttestationResponse;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.ClientAssertionExtensionOutputs;
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.entity.AuthUserWebAuthn;
import com.zhiyu.ufp.common.cache.CacheKeys;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.auth.mapper.AuthUserWebAuthnMapper;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
public class WebAuthnService {

    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final int CHALLENGE_BYTES = 32;

    private final RelyingParty relyingParty;
    private final AuthUserWebAuthnMapper webAuthnMapper;
    private final AuthUserMapper authUserMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    public WebAuthnService(final RelyingPartyIdentity rpIdentity,
                           final WebAuthnCredentialRepository credentialRepository,
                           final AuthUserWebAuthnMapper webAuthnMapper,
                           final AuthUserMapper authUserMapper,
                           final StringRedisTemplate redisTemplate,
                           final ObjectMapper objectMapper) {
        this.relyingParty = RelyingParty.builder()
                .identity(rpIdentity)
                .credentialRepository(credentialRepository)
                .build();
        this.webAuthnMapper = webAuthnMapper;
        this.authUserMapper = authUserMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper.copy();
    }

    public WebAuthnStartResult startRegistration(final Long userId) throws IOException {
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }

        byte[] userIdBytes = String.valueOf(userId).getBytes(StandardCharsets.UTF_8);
        UserIdentity userIdentity = UserIdentity.builder()
                .name(user.getAuthUserUsername())
                .displayName(user.getAuthUserNick() != null ? user.getAuthUserNick() : user.getAuthUserUsername())
                .id(new ByteArray(userIdBytes))
                .build();

        PublicKeyCredentialCreationOptions options = relyingParty.startRegistration(
                StartRegistrationOptions.builder()
                        .user(userIdentity)
                        .authenticatorSelection(
                                com.yubico.webauthn.data.AuthenticatorSelectionCriteria.builder()
                                        .residentKey(ResidentKeyRequirement.PREFERRED)
                                        .userVerification(
                                                com.yubico.webauthn.data.UserVerificationRequirement.PREFERRED)
                                        .build())
                        .build());

        String challengeId = generateChallengeId();
        redisTemplate.opsForValue().set(CacheKeys.WEBAUTHN_CHALLENGE + challengeId,
                options.toJson(), CHALLENGE_TTL);

        return new WebAuthnStartResult(challengeId, options.toCredentialsCreateJson());
    }

    public void finishRegistration(final String challengeId,
                                    final String credentialJson) throws IOException, RegistrationFailedException {
        String optionsJson = redisTemplate.opsForValue().get(CacheKeys.WEBAUTHN_CHALLENGE + challengeId);
        if (optionsJson == null) {
            throw new BizException(BizErrorCode.ACTION_EXPIRED);
        }
        redisTemplate.delete(CacheKeys.WEBAUTHN_CHALLENGE + challengeId);

        PublicKeyCredentialCreationOptions options =
                PublicKeyCredentialCreationOptions.fromJson(optionsJson);

        PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> credential =
                PublicKeyCredential.parseRegistrationResponseJson(credentialJson);

        RegistrationResult result = relyingParty.finishRegistration(
                FinishRegistrationOptions.builder()
                        .request(options)
                        .response(credential)
                        .build());

        Long userId = Long.parseLong(new String(options.getUser().getId().getBytes(), StandardCharsets.UTF_8));
        AuthUserWebAuthn entity = AuthUserWebAuthn.builder()
                .authUserId(userId)
                .credentialId(result.getKeyId().getId().getBase64Url())
                .publicKey(result.getPublicKeyCose().getBase64())
                .signCount(Math.toIntExact(result.getSignatureCount()))
                .enabled(AuthUserWebAuthn.ENABLED)
                .createdTime(LocalDateTime.now())
                .build();
        webAuthnMapper.insert(entity);
    }

    public WebAuthnStartResult startAuthentication(final String username) throws IOException {
        AssertionRequest assertionRequest = relyingParty.startAssertion(
                StartAssertionOptions.builder()
                        .username(username)
                        .userVerification(
                                com.yubico.webauthn.data.UserVerificationRequirement.PREFERRED)
                        .build());

        String challengeId = generateChallengeId();
        redisTemplate.opsForValue().set(CacheKeys.WEBAUTHN_CHALLENGE + challengeId,
                assertionRequest.toJson(), CHALLENGE_TTL);

        return new WebAuthnStartResult(challengeId, assertionRequest.toCredentialsGetJson());
    }

    public AssertionResult finishAuthentication(final String challengeId,
                                                  final String credentialJson)
            throws IOException, AssertionFailedException {
        String requestJson = redisTemplate.opsForValue()
                .get(CacheKeys.WEBAUTHN_CHALLENGE + challengeId);
        if (requestJson == null) {
            throw new BizException(BizErrorCode.ACTION_EXPIRED);
        }
        redisTemplate.delete(CacheKeys.WEBAUTHN_CHALLENGE + challengeId);

        AssertionRequest request = AssertionRequest.fromJson(requestJson);

        PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> credential =
                PublicKeyCredential.parseAssertionResponseJson(credentialJson);

        AssertionResult result = relyingParty.finishAssertion(
                FinishAssertionOptions.builder()
                        .request(request)
                        .response(credential)
                        .build());

        if (!result.isSuccess()) {
            throw new BizException(BizErrorCode.WEBAUTHN_FAILED);
        }

        updateSignCount(result);

        return result;
    }

    private void updateSignCount(final AssertionResult result) {
        String credId = result.getCredentialId().getBase64Url();
        AuthUserWebAuthn credential = webAuthnMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuthUserWebAuthn>()
                        .eq(AuthUserWebAuthn::getCredentialId, credId));
        if (credential != null) {
            credential.setSignCount(Math.toIntExact(result.getSignatureCount()));
            credential.setLastUsedTime(LocalDateTime.now());
            webAuthnMapper.updateById(credential);
        }
    }

    private String generateChallengeId() {
        byte[] bytes = new byte[CHALLENGE_BYTES];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
