package com.zhiyu.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.auth.mapper.AuthUserWebAuthnMapper;
import com.zhiyu.ufp.auth.webauthn.WebAuthnCredentialRepository;
import com.zhiyu.ufp.auth.webauthn.WebAuthnService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class WebAuthnConfigTest {

    @Mock
    private WebAuthnCredentialRepository credentialRepository;

    @Mock
    private AuthUserWebAuthnMapper webAuthnMapper;

    @Mock
    private AuthUserMapper authUserMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateWebAuthnServiceWithDefaultValues() {
        WebAuthnConfig config = new WebAuthnConfig();
        // 通过反射设置默认字段
        setField(config, "relyingPartyId", "localhost");
        setField(config, "relyingPartyName", "ZhiYu");

        WebAuthnService service = config.webAuthnService(
                credentialRepository, webAuthnMapper, authUserMapper,
                redisTemplate, objectMapper);

        assertThat(service).isNotNull();
    }

    @Test
    void shouldCreateWebAuthnServiceWithCustomValues() {
        WebAuthnConfig config = new WebAuthnConfig();
        setField(config, "relyingPartyId", "auth.example.com");
        setField(config, "relyingPartyName", "TestApp");

        WebAuthnService service = config.webAuthnService(
                credentialRepository, webAuthnMapper, authUserMapper,
                redisTemplate, objectMapper);

        assertThat(service).isNotNull();
    }

    private void setField(Object target, String fieldName, String value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
