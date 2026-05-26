package com.zhiyu.auth.service;

import cn.hutool.core.util.RandomUtil;
import com.zhiyu.auth.dto.CaptchaResponse;
import com.zhiyu.ufp.common.cache.CacheKeys;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int WIDTH = 130;
    private static final int HEIGHT = 48;
    private static final int CODE_COUNT = 4;
    private static final int OVAL_SIZE = 2;

    // Background color
    private static final int BG_R = 10;
    private static final int BG_G = 14;
    private static final int BG_B = 39;

    // Interference lines
    private static final int LINE_COUNT = 5;
    private static final int LINE_ALPHA_MIN = 40;
    private static final int LINE_ALPHA_MAX = 100;
    private static final int LINE_R_MAX = 80;
    private static final int LINE_G_MIN = 100;
    private static final int LINE_G_MAX = 200;
    private static final int LINE_B_MIN = 180;
    private static final int LINE_B_MAX = 255;
    private static final float LINE_STROKE_MIN = 1f;
    private static final float LINE_STROKE_MAX = 2f;
    private static final int LINE_X_OFFSET_MIN = -30;
    private static final int LINE_X_OFFSET_MAX = 30;
    private static final int LINE_Y_OFFSET_MIN = -20;
    private static final int LINE_Y_OFFSET_MAX = 20;

    // Noise dots
    private static final int NOISE_COUNT = 40;
    private static final int NOISE_ALPHA_MIN = 30;
    private static final int NOISE_ALPHA_MAX = 80;
    private static final int NOISE_R = 0;
    private static final int NOISE_G = 200;
    private static final int NOISE_B = 255;

    // Text position
    private static final int TEXT_BASE_X = 18;
    private static final int TEXT_BASE_Y = 34;
    private static final int TEXT_SPACING = 28;
    private static final int TEXT_Y_OFFSET_MIN = -3;
    private static final int TEXT_Y_OFFSET_MAX = 3;
    private static final double TEXT_ANGLE_MIN = -0.15;
    private static final double TEXT_ANGLE_MAX = 0.15;

    // Text color
    private static final int TEXT_R_MAX = 100;
    private static final int TEXT_G_MIN = 180;
    private static final int TEXT_G_MAX = 255;
    private static final int TEXT_B_MIN = 220;
    private static final int TEXT_B_MAX = 255;

    private static final String CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final Font[] FONTS = {
            new Font("Arial", Font.BOLD, 28),
            new Font("Helvetica", Font.BOLD, 28),
    };

    private final StringRedisTemplate redisTemplate;

    public CaptchaResponse generate(final String sceneId) {
        String code = RandomUtil.randomString(CHARS, CODE_COUNT);
        String token = UUID.randomUUID().toString().replace("-", "");

        redisTemplate.opsForValue().set(CacheKeys.CAPTCHA_PREFIX + token, code, TTL);

        return CaptchaResponse.builder()
                .captchaToken(token)
                .captchaImage(generateImage(code))
                .build();
    }

    private String generateImage(final String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(new Color(BG_R, BG_G, BG_B));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        for (int i = 0; i < LINE_COUNT; i++) {
            int alpha = RandomUtil.randomInt(LINE_ALPHA_MIN, LINE_ALPHA_MAX);
            int r = RandomUtil.randomInt(0, LINE_R_MAX);
            int g2 = RandomUtil.randomInt(LINE_G_MIN, LINE_G_MAX);
            int b = RandomUtil.randomInt(LINE_B_MIN, LINE_B_MAX);
            g.setColor(new Color(r, g2, b, alpha));
            g.setStroke(new BasicStroke(
                    RandomUtil.randomFloat(LINE_STROKE_MIN, LINE_STROKE_MAX)));
            int x1 = RandomUtil.randomInt(0, WIDTH);
            int y1 = RandomUtil.randomInt(0, HEIGHT);
            g.drawLine(x1, y1,
                    x1 + RandomUtil.randomInt(LINE_X_OFFSET_MIN, LINE_X_OFFSET_MAX),
                    y1 + RandomUtil.randomInt(LINE_Y_OFFSET_MIN, LINE_Y_OFFSET_MAX));
        }

        for (int i = 0; i < NOISE_COUNT; i++) {
            int alpha = RandomUtil.randomInt(NOISE_ALPHA_MIN, NOISE_ALPHA_MAX);
            g.setColor(new Color(NOISE_R, NOISE_G, NOISE_B, alpha));
            int x = RandomUtil.randomInt(0, WIDTH);
            int y = RandomUtil.randomInt(0, HEIGHT);
            g.fillOval(x, y, OVAL_SIZE, OVAL_SIZE);
        }

        for (int i = 0; i < code.length(); i++) {
            g.setFont(FONTS[RandomUtil.randomInt(0, FONTS.length)]);
            int r = RandomUtil.randomInt(0, TEXT_R_MAX);
            int gVal = RandomUtil.randomInt(TEXT_G_MIN, TEXT_G_MAX);
            int b = RandomUtil.randomInt(TEXT_B_MIN, TEXT_B_MAX);
            g.setColor(new Color(r, gVal, b));

            double angle = RandomUtil.randomDouble(TEXT_ANGLE_MIN, TEXT_ANGLE_MAX);
            int yOff = RandomUtil.randomInt(TEXT_Y_OFFSET_MIN, TEXT_Y_OFFSET_MAX);
            int cx = TEXT_BASE_X + i * TEXT_SPACING;
            int cy = TEXT_BASE_Y + yOff;
            g.rotate(angle, cx, cy);
            g.drawString(String.valueOf(code.charAt(i)), cx, cy);
            g.rotate(-angle, cx, cy);
        }

        g.dispose();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return "data:image/png;base64,"
                    + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new BizException(BizErrorCode.INTERNAL_ERROR, e);
        }
    }

    public void verify(final String token, final String code) {
        String key = CacheKeys.CAPTCHA_PREFIX + token;
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
