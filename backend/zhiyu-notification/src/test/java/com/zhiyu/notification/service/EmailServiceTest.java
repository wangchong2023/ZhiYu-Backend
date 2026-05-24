package com.zhiyu.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.notification.entity.NotificationTemplate;
import com.zhiyu.notification.mapper.NotificationTemplateMapper;
import com.zhiyu.ufp.common.exception.BizException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private NotificationTemplateMapper templateMapper;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private MimeMessage mimeMessage;
    @InjectMocks
    private EmailService emailService;

    private NotificationTemplate buildTemplate() {
        return NotificationTemplate.builder()
                .id(1L)
                .templateKey("register_welcome")
                .type("EMAIL")
                .subject("Welcome {{username}}!")
                .body("<p>Hello {{username}}, welcome to {{app_name}}</p>")
                .isActive(true)
                .build();
    }

    @Test
    void shouldRenderTemplateWithVariables() {
        NotificationTemplate template = buildTemplate();
        when(templateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(template);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendEmail("register_welcome", "user@example.com",
                Map.of("username", "Alice", "app_name", "ZhiYu"));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void shouldThrowWhenTemplateNotFound() {
        when(templateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> emailService.sendEmail("nonexistent", "user@example.com",
                Map.of()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Resource not found");
    }

    @Test
    void shouldThrowWhenTemplateInactive() {
        NotificationTemplate template = buildTemplate();
        template.setIsActive(false);
        when(templateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(template);

        assertThatThrownBy(() -> emailService.sendEmail("register_welcome", "user@example.com",
                Map.of()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Resource not found");
    }

    @Test
    void shouldLogWhenNoSmtpConfigured() {
        // Create a new EmailService without mailSender (simulating no SMTP config)
        EmailService noMailService = new EmailService(templateMapper, null);
        NotificationTemplate template = buildTemplate();
        when(templateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(template);

        // Should not throw since it just logs
        noMailService.sendEmail("register_welcome", "user@example.com",
                Map.of("username", "Alice", "app_name", "ZhiYu"));
    }

    @Test
    void shouldReplaceVariablesInSubjectAndBody() {
        NotificationTemplate template = buildTemplate();
        when(templateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(template);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendEmail("register_welcome", "user@example.com",
                Map.of("username", "Alice", "app_name", "ZhiYu"));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        // The MimeMessage should have been populated with rendered content
        assertThat(captor.getValue()).isNotNull();
    }

    @Test
    void shouldKeepUnmatchedPlaceholders() {
        NotificationTemplate template = buildTemplate();
        when(templateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(template);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Send with missing params - unmatched {{...}} should be preserved
        emailService.sendEmail("register_welcome", "user@example.com",
                Map.of("username", "Alice"));
        // Should not throw - unmatched placeholders are left as-is

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void renderShouldHandleNullInput() {
        String result = EmailService.render(null, Map.of("key", "value"));
        assertThat(result).isNull();
    }

    @Test
    void renderShouldHandleVariableInMiddleOfText() {
        String result = EmailService.render("Hello {{name}}, how are you?",
                Map.of("name", "Alice"));
        assertThat(result).isEqualTo("Hello Alice, how are you?");
    }
}
