package com.zhiyu.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.notification.entity.NotificationTemplate;
import com.zhiyu.notification.mapper.NotificationTemplateMapper;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String TEMPLATE_VAR_PATTERN = "\\{\\{\\s*(\\w+)\\s*\\}\\}";

    private final NotificationTemplateMapper templateMapper;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    /**
     * Send an email using a template by templateKey.
     * If no SMTP (JavaMailSender) is configured, logs the email content at INFO level.
     *
     * @param templateKey the template identifier (e.g., "register_welcome")
     * @param to          recipient email address
     * @param params      template variable values (e.g., {"username": "Alice", "app_name": "ZhiYu"})
     */
    public void sendEmail(final String templateKey, final String to,
                          final Map<String, String> params) {
        NotificationTemplate template = loadAndValidateTemplate(templateKey, "EMAIL");
        String subject = render(template.getSubject(), params);
        String body = render(template.getBody(), params);

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();

        if (mailSender == null) {
            log.info("[Email mock] To: {} | Subject: {} | Body: {}",
                    to, subject, body);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(message);
            log.info("Email sent successfully to {} with template '{}'", to, templateKey);
        } catch (MessagingException e) {
            if (log.isErrorEnabled()) {
                log.error("Failed to send email to {} with template '{}': {}",
                        to, templateKey, e.getMessage(), e);
            }
            throw new BizException(BizErrorCode.INTERNAL_ERROR, e);
        }
    }

    private NotificationTemplate loadAndValidateTemplate(final String templateKey,
                                                         final String expectedType) {
        NotificationTemplate template = templateMapper.selectOne(
                new LambdaQueryWrapper<NotificationTemplate>()
                        .eq(NotificationTemplate::getTemplateKey, templateKey));

        if (template == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(template.getIsActive())) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!expectedType.equals(template.getType())) {
            if (log.isWarnEnabled()) {
                log.warn("Template '{}' type is '{}', expected '{}'",
                        templateKey, template.getType(), expectedType);
            }
        }
        return template;
    }

    /**
     * Replace {{varName}} placeholders in the template string with values from the params map.
     */
    static String render(final String input, final Map<String, String> params) {
        if (input == null) {
            return null;
        }
        return java.util.regex.Pattern.compile(TEMPLATE_VAR_PATTERN)
                .matcher(input)
                .replaceAll(match -> {
                    String key = match.group(1);
                    String value = params.get(key);
                    return value != null ? java.util.regex.Matcher.quoteReplacement(value) : match.group();
                });
    }
}
