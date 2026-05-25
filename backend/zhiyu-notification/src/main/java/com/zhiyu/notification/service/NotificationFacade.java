package com.zhiyu.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.notification.entity.NotificationTemplate;
import com.zhiyu.notification.mapper.NotificationTemplateMapper;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationFacade {

    private static final String TYPE_EMAIL = "EMAIL";
    private static final String TYPE_SMS = "SMS";

    private final NotificationTemplateMapper templateMapper;
    private final EmailService emailService;
    private final SmsService smsService;

    /**
     * Send a notification using a template, routing to the appropriate channel.
     * The template type (EMAIL/SMS) determines which service is used.
     *
     * @param templateKey the template identifier
     * @param recipient   email address or phone number
     * @param params      template variable values
     */
    public void send(final String templateKey, final String recipient,
                     final Map<String, String> params) {
        NotificationTemplate template = templateMapper.selectOne(
                new LambdaQueryWrapper<NotificationTemplate>()
                        .eq(NotificationTemplate::getTemplateKey, templateKey));

        if (template == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(template.getIsActive())) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }

        switch (template.getType()) {
            case TYPE_EMAIL -> emailService.sendEmail(templateKey, recipient, params);
            case TYPE_SMS -> smsService.sendSms(templateKey, recipient, params);
            default -> {
                if (log.isWarnEnabled()) {
                    log.warn("Unsupported notification type '{}' for template '{}'",
                            template.getType(), templateKey);
                }
                throw new BizException(BizErrorCode.UNSUPPORTED_CONTENT_TYPE);
            }
        }
    }
}
