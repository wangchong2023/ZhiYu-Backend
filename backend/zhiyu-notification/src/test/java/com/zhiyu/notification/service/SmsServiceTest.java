package com.zhiyu.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.notification.entity.NotificationTemplate;
import com.zhiyu.notification.mapper.NotificationTemplateMapper;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsServiceTest {

    @Mock
    private NotificationTemplateMapper templateMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @InjectMocks
    private SmsService smsService;

    private NotificationTemplate buildSmsTemplate() {
        return NotificationTemplate.builder()
                .id(2L)
                .templateKey("welcome_sms")
                .type("SMS")
                .body("Welcome {{username}} to {{app_name}}!")
                .isActive(true)
                .build();
    }

    @Test
    void shouldRenderSmsTemplate() {
        when(templateMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(buildSmsTemplate());

        smsService.sendSms("welcome_sms", "13800138000",
                Map.of("username", "Alice", "app_name", "ZhiYu"));
        // Should not throw — logs at INFO level
    }

    @Test
    void shouldStoreCodeInRedisForVerificationTemplates() {
        when(templateMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(buildSmsTemplate());
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        smsService.sendSms("welcome_sms", "13800138000",
                Map.of("username", "Alice", "app_name", "ZhiYu", "code", "123456"));

        verify(valueOps).set(eq("sms:welcome_sms:13800138000"), eq("123456"),
                eq(Duration.ofMinutes(5)));
    }

    @Test
    void shouldNotStoreCodeInRedisWhenNoCodeParam() {
        when(templateMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(buildSmsTemplate());

        smsService.sendSms("welcome_sms", "13800138000",
                Map.of("username", "Alice"));

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void shouldThrowWhenTemplateNotFound() {
        when(templateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> smsService.sendSms("nonexistent", "13800138000",
                Map.of()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Resource not found");
    }

    @Test
    void shouldThrowWhenTemplateInactive() {
        NotificationTemplate template = buildSmsTemplate();
        template.setIsActive(false);
        when(templateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(template);

        assertThatThrownBy(() -> smsService.sendSms("welcome_sms", "13800138000",
                Map.of()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Resource not found");
    }

    @Test
    void shouldVerifyCorrectCode() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("sms:email_verify:13800138000")).thenReturn("123456");

        boolean result = smsService.verifyCode("email_verify", "13800138000", "123456");
        assertThat(result).isTrue();
        verify(redisTemplate).delete("sms:email_verify:13800138000");
    }

    @Test
    void shouldRejectWrongCode() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("sms:email_verify:13800138000")).thenReturn("123456");

        boolean result = smsService.verifyCode("email_verify", "13800138000", "654321");
        assertThat(result).isFalse();
        // Should not delete on wrong code
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void shouldReturnFalseWhenNoCodeStored() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("sms:email_verify:13800138000")).thenReturn(null);

        boolean result = smsService.verifyCode("email_verify", "13800138000", "123456");
        assertThat(result).isFalse();
    }
}
