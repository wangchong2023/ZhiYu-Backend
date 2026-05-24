package com.zhiyu.auth.service;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.zhiyu.auth.dto.CaptchaResponse;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String PREFIX = "captcha:";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int CAPTCHA_WIDTH = 130;
    private static final int CAPTCHA_HEIGHT = 48;
    private static final int CAPTCHA_CODE_COUNT = 4;
    private static final int CAPTCHA_INTERFERENCE = 20;
    private final StringRedisTemplate redisTemplate;

    public CaptchaResponse generate(final String sceneId) {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(
                CAPTCHA_WIDTH, CAPTCHA_HEIGHT, CAPTCHA_CODE_COUNT, CAPTCHA_INTERFERENCE);
        String code = captcha.getCode();
        String token = UUID.randomUUID().toString().replace("-", "");

        redisTemplate.opsForValue().set(PREFIX + token, code, TTL);

        return CaptchaResponse.builder()
                .captchaToken(token)
                .captchaImage(captcha.getImageBase64Data())
                .build();
    }

    public void verify(final String token, final String code) {
        String key = PREFIX + token;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null) {
            throw new BizException(BizErrorCode.VERIFY_CODE_INCORRECT);
        }
        if (!stored.equalsIgnoreCase(code)) {
            throw new BizException(BizErrorCode.VERIFY_CODE_INCORRECT);
        }
        redisTemplate.delete(key);
    }
}
