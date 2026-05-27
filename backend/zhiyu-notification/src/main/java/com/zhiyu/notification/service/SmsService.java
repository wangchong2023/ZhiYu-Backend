/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: SmsService.java
 * 创建时间: 2026-05-27
 * 描述: 短信通知服务，基于模板发送短信并支持验证码的 Redis 存储与核验。
 *       当前实现为 Mock 模式，以日志记录替代真实短信发送，便于开发和测试阶段使用。
 */
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

/**
 * 类名: SmsService
 * 描述: 短信发送与验证码核验服务。
 *       通过模板 Key 查找短信模板，渲染后以 Mock 方式输出日志；
 *       对含有 "code" 变量的模板，额外将验证码写入 Redis 并设置 TTL。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService {

    /** 短信验证码在 Redis 中的默认过期时长：5 分钟 */
    private static final Duration CODE_TTL = Duration.ofMinutes(5);

    private final NotificationTemplateMapper templateMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * 描述: 基于指定模板发送短信（当前为 Mock 模式，仅记录日志）。
     *      若模板变量中含有 "code"，则将验证码同步存入 Redis 供后续核验。
     *
     * @param templateKey 短信模板标识（如 "welcome_sms"、"verify_code_sms"）
     * @param phone       接收短信的手机号码
     * @param params      模板变量键值对（如 {"code": "123456"}）
     * @throws BizException 模板不存在或未启用时抛出
     */
    public void sendSms(final String templateKey, final String phone,
                        final Map<String, String> params) {
        NotificationTemplate template = loadAndValidateTemplate(templateKey, "SMS");
        String body = EmailService.render(template.getBody(), params);

        log.info("[短信 Mock] 收件人: {} | 模板: {} | 内容: {}",
                phone, templateKey, body);

        // 若模板变量中包含验证码，将其写入 Redis 并设置 TTL 以支持后续核验
        String code = params.get("code");
        if (code != null) {
            String redisKey = CacheKeys.key(CacheKeys.SMS_CODE, templateKey, phone);
            redisTemplate.opsForValue().set(redisKey, code, CODE_TTL);
            log.info("短信验证码已写入 Redis：key={}, ttl={}", redisKey, CODE_TTL);
        }
    }

    /**
     * 描述: 核验用户输入的短信验证码是否与 Redis 中存储的一致。
     *      核验成功后立即删除 Redis 中的记录，防止重复使用。
     *
     * @param templateKey 发送时使用的模板标识（用于拼接 Redis Key）
     * @param phone       手机号码
     * @param code        用户提交的验证码
     * @return true 表示核验通过；false 表示验证码不存在或不匹配
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
            // 核验成功后删除 Redis 中的验证码，保证一次性使用
            redisTemplate.delete(redisKey);
        }
        return matches;
    }

    /**
     * 描述: 加载并校验通知模板。
     *      校验模板存在性、启用状态及类型匹配（SMS/EMAIL），类型不符时仅记录警告。
     *
     * @param templateKey  模板标识
     * @param expectedType 期望的模板类型（"SMS" 或 "EMAIL"）
     * @return 通过校验的通知模板实体
     * @throws BizException 模板不存在或未启用时抛出
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
            log.warn("模板类型不匹配：templateKey='{}', 实际类型='{}', 期望类型='{}'",
                    templateKey, template.getType(), expectedType);
        }
        return template;
    }
}
