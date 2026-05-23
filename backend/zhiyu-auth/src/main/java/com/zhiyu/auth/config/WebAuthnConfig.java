package com.zhiyu.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.auth.mapper.AuthUserWebAuthnMapper;
import com.zhiyu.ufp.auth.webauthn.WebAuthnCredentialRepository;
import com.zhiyu.ufp.auth.webauthn.WebAuthnService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class WebAuthnConfig {

    @Value("${zhiyu.auth.webauthn.relying-party-id:localhost}")
    private String relyingPartyId;

    @Value("${zhiyu.auth.webauthn.relying-party-name:ZhiYu}")
    private String relyingPartyName;

    @Bean
    public WebAuthnService webAuthnService(
            final WebAuthnCredentialRepository credentialRepository,
            final AuthUserWebAuthnMapper webAuthnMapper,
            final AuthUserMapper authUserMapper,
            final StringRedisTemplate redisTemplate,
            final ObjectMapper objectMapper) {
        RelyingPartyIdentity rpIdentity = RelyingPartyIdentity.builder()
                .id(relyingPartyId)
                .name(relyingPartyName)
                .build();
        return new WebAuthnService(rpIdentity, credentialRepository,
                webAuthnMapper, authUserMapper, redisTemplate, objectMapper);
    }
}
