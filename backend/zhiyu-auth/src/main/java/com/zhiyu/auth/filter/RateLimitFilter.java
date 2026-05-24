package com.zhiyu.auth.filter;

import com.zhiyu.common.web.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_KEY_PREFIX = "rate:";
    private static final int DEFAULT_RPM = 100;
    private static final int WINDOW_SECONDS = 60;
    private static final int TOO_MANY_REQUESTS = 429;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain chain) throws ServletException, IOException {
        String key = buildRateKey(request);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            count = 0L;
        }

        if (count == 1) {
            redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
        }

        if (count > DEFAULT_RPM) {
            log.warn("Rate limit exceeded for key={}, count={}", key, count);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getWriter(),
                    ApiResponse.fail(TOO_MANY_REQUESTS, "请求过于频繁，请稍后重试"));
            return;
        }

        chain.doFilter(request, response);
    }

    private String buildRateKey(final HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return RATE_KEY_PREFIX + ip;
    }
}
