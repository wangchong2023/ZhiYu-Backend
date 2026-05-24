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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationFacadeTest {

    @Mock
    private NotificationTemplateMapper templateMapper;
    @Mock
    private EmailService emailService;
    @Mock
    private SmsService smsService;
    @InjectMocks
    private NotificationFacade notificationFacade;

    @Test
    void shouldRouteToEmailServiceForEmailType() {
        NotificationTemplate template = NotificationTemplate.builder()
                .id(1L)
                .templateKey("register_welcome")
                .type("EMAIL")
                .subject("Welcome")
                .body("Hello")
                .isActive(true)
                .build();
        when(templateMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(template);

        Map<String, String> params = Map.of("username", "Alice");
        notificationFacade.send("register_welcome", "user@example.com", params);

        verify(emailService).sendEmail(eq("register_welcome"),
                eq("user@example.com"), eq(params));
        verify(smsService, never()).sendSms(any(), any(), any());
    }

    @Test
    void shouldRouteToSmsServiceForSmsType() {
        NotificationTemplate template = NotificationTemplate.builder()
                .id(2L)
                .templateKey("welcome_sms")
                .type("SMS")
                .body("Welcome {{username}}")
                .isActive(true)
                .build();
        when(templateMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(template);

        Map<String, String> params = Map.of("username", "Alice");
        notificationFacade.send("welcome_sms", "13800138000", params);

        verify(smsService).sendSms(eq("welcome_sms"),
                eq("13800138000"), eq(params));
        verify(emailService, never()).sendEmail(any(), any(), any());
    }

    @Test
    void shouldThrowWhenTemplateNotFound() {
        when(templateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> notificationFacade.send("nonexistent",
                "user@example.com", Map.of()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Resource not found");
    }

    @Test
    void shouldThrowWhenTemplateInactive() {
        NotificationTemplate template = NotificationTemplate.builder()
                .id(1L)
                .templateKey("register_welcome")
                .type("EMAIL")
                .isActive(false)
                .build();
        when(templateMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(template);

        assertThatThrownBy(() -> notificationFacade.send("register_welcome",
                "user@example.com", Map.of()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Resource not found");
    }
}
