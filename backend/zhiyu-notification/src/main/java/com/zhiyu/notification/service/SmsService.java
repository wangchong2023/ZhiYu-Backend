package com.zhiyu.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.notification.entity.NotificationTemplate;
import com.zhiyu.notification.mapper.NotificationTemplateMapper;
import com.zhiyu.ufp.common.cache.CacheKeys;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);

    private final NotificationTemplateMapper templateMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * Send an SMS using a template by templateKey.
     * Currently in mock mode: logs the SMS content at INFO level.
     * For verification-code templates, also stores the code in Redis.
     *
     * @param templateKey the template identifier (e.g., "welcome_sms")
     * @param phone       recipient phone number
     * @param params      template variable values
     */
    public void sendSms(final String templateKey, final String phone,
                        final Map<String, String> params) {
        NotificationTemplate template = loadAndValidateTemplate(templateKey, "SMS");
        String body = EmailService.render(template.getBody(), params);

        log.info("[SMS mock] To: {} | Template: {} | Content: {}",
                phone, templateKey, body);

        // Store verification code in Redis if template contains a "code" variable
        String code = params.get("code");
        if (code != null) {
            String redisKey = CacheKeys.key(CacheKeys.SMS_CODE, templateKey, phone);
            redisTemplate.opsForValue().set(redisKey, code, CODE_TTL);
            log.info("SMS code stored in Redis: key={}, ttl={}", redisKey, CODE_TTL);
        }
    }

    /**
     * Verify an SMS code from Redis.
     *
     * @param templateKey the template used (e.g., "email_verify")
     * @param phone       the phone number
     * @param code        the code to verify
     * @return true if the code matches
     */
    public boolean verifyCode(final String templateKey, final String phone,
                              final String code) {
        String redisKey = CacheKeys.key(CacheKeys.SMS_CODE, templateKey, phone);
        String stored = redisTemplate.opsForValue().get(redisKey);
        if (stored == null) {
            return false;
        }
        boolean matches = stored.equals(code);
        if (matches) {
            redisTemplate.delete(redisKey);
        }
        return matches;
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
            log.warn("Template '{}' type is '{}', expected '{}'",
                    templateKey, template.getType(), expectedType);
        }
        return template;
    }
}
