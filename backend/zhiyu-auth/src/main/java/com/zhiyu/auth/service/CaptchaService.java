package com.zhiyu.auth.service;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.zhiyu.auth.dto.CaptchaResponse;
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
    private final StringRedisTemplate redisTemplate;

    public CaptchaResponse generate(String sceneId) {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(130, 48, 4, 20);
        String code = captcha.getCode();
        String token = UUID.randomUUID().toString().replace("-", "");

        redisTemplate.opsForValue().set(PREFIX + token, code, TTL);

        return CaptchaResponse.builder()
                .captchaToken(token)
                .captchaImage("data:image/png;base64," + captcha.getImageBase64Data())
                .build();
    }

    public void verify(String token, String code) {
        String key = PREFIX + token;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null) {
            throw new BizException(40110, "验证码已过期");
        }
        if (!stored.equalsIgnoreCase(code)) {
            throw new BizException(40109, "验证码错误");
        }
        redisTemplate.delete(key);
    }
}
