/**
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: EmailService.java
 * 创建时间: 2026-05-27
 * 描述: 邮件发送业务服务。从数据库模板表动态读取邮件模板，渲染占位符参数，并通过 JavaMail 协议组件执行邮件投递。
 */
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

/**
 * 类名: EmailService
 * 描述: 统一的邮件通知服务组件。若未配置 SMTP（JavaMailSender），则在 INFO 日志级别下模拟输出邮件发送行为。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    // 匹配模板中 {{ variable }} 变量占位符的正则表达式模式
    private static final String TEMPLATE_VAR_PATTERN = "\\{\\{\\s*(\\w+)\\s*\\}\\}";

    private final NotificationTemplateMapper templateMapper;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    /**
     * 描述: 根据指定的模板键名，对目标邮箱发送经参数渲染后的多语言邮件通知。
     *      若系统检测到未配置 JavaMailSender（SMTP 服务未启用），则自动在日志中模拟输出邮件内容，防止阻断业务。
     * @param templateKey 模板标识键名 (例如: "register_welcome")
     * @param to 目标收件人邮箱地址
     * @param params 用于替换模板中占位符的参数键值对映射
     * @throws BizException 若发生底层邮件投递失败，则包装为 INTERNAL_ERROR 业务异常抛出
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

    /**
     * 描述: 私有辅助方法，从数据库加载指定模板并进行启用及类型校验。
     */
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
        if (!expectedType.equals(template.getType()) && log.isWarnEnabled()) {
            log.warn("Template '{}' type is '{}', expected '{}'",
                    templateKey, template.getType(), expectedType);
        }
        return template;
    }

    /**
     * 描述: 对包含 {{变量名}} 格式的输入字符串进行正则匹配，并用 params 映射中对应的值进行安全替换渲染。
     * @param input 原始模板文本内容
     * @param params 变量参数映射
     * @return 渲染完毕后的纯文本字符串
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
