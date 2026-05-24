package com.zhiyu.auth.service;

import cn.hutool.core.util.RandomUtil;
import com.zhiyu.auth.dto.CaptchaResponse;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String PREFIX = "captcha:";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int WIDTH = 130;
    private static final int HEIGHT = 48;
    private static final int CODE_COUNT = 4;

    private static final String CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final Font[] FONTS = {
            new Font("Arial", Font.BOLD, 28),
            new Font("Helvetica", Font.BOLD, 28),
    };

    private final StringRedisTemplate redisTemplate;

    public CaptchaResponse generate(final String sceneId) {
        String code = RandomUtil.randomString(CHARS, CODE_COUNT);
        String token = UUID.randomUUID().toString().replace("-", "");

        redisTemplate.opsForValue().set(PREFIX + token, code, TTL);

        return CaptchaResponse.builder()
                .captchaToken(token)
                .captchaImage(generateImage(code))
                .build();
    }

    private String generateImage(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Dark cosmic background
        g.setColor(new Color(10, 14, 39));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // Interference lines — subtle cyan/blue tones
        for (int i = 0; i < 5; i++) {
            int alpha = RandomUtil.randomInt(40, 100);
            int r = RandomUtil.randomInt(0, 80);
            int g2 = RandomUtil.randomInt(100, 200);
            int b = RandomUtil.randomInt(180, 255);
            g.setColor(new Color(r, g2, b, alpha));
            g.setStroke(new BasicStroke(RandomUtil.randomFloat(1f, 2f)));
            int x1 = RandomUtil.randomInt(0, WIDTH);
            int y1 = RandomUtil.randomInt(0, HEIGHT);
            g.drawLine(x1, y1, x1 + RandomUtil.randomInt(-30, 30), y1 + RandomUtil.randomInt(-20, 20));
        }

        // Noise dots
        for (int i = 0; i < 40; i++) {
            int alpha = RandomUtil.randomInt(30, 80);
            g.setColor(new Color(0, 200, 255, alpha));
            int x = RandomUtil.randomInt(0, WIDTH);
            int y = RandomUtil.randomInt(0, HEIGHT);
            g.fillOval(x, y, 2, 2);
        }

        // Code text — cyan with slight per-character offset
        for (int i = 0; i < code.length(); i++) {
            g.setFont(FONTS[RandomUtil.randomInt(0, FONTS.length)]);
            int r = RandomUtil.randomInt(0, 100);
            int gVal = RandomUtil.randomInt(180, 255);
            int b = RandomUtil.randomInt(220, 255);
            g.setColor(new Color(r, gVal, b));

            double angle = RandomUtil.randomDouble(-0.15, 0.15);
            int yOff = RandomUtil.randomInt(-3, 3);
            int cx = 18 + i * 28;
            int cy = 34 + yOff;
            g.rotate(angle, cx, cy);
            g.drawString(String.valueOf(code.charAt(i)), cx, cy);
            g.rotate(-angle, cx, cy);
        }

        g.dispose();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new BizException(BizErrorCode.INTERNAL_ERROR);
        }
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
