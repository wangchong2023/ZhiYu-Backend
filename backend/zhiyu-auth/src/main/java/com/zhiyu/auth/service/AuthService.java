package com.zhiyu.auth.service;

import com.zhiyu.auth.dto.LoginRequest;
import com.zhiyu.auth.dto.LoginResponse;
import com.zhiyu.auth.dto.RefreshRequest;
import com.zhiyu.auth.dto.RegisterRequest;
import com.zhiyu.auth.dto.RegisterResponse;
import com.zhiyu.auth.dto.SendSmsRequest;
import com.zhiyu.auth.dto.TotpSetupResponse;
import com.zhiyu.ufp.common.cache.CacheKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int SMS_CODE_BOUND = 1_000_000;
    private static final int SMS_CODE_TTL_MINUTES = 5;

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final TotpManagementService totpManagementService;
    private final StringRedisTemplate redisTemplate;

    public RegisterResponse register(final RegisterRequest request) {
        return registrationService.register(request);
    }

    public LoginResponse login(final LoginRequest request) {
        return loginService.login(request);
    }

    public LoginResponse refresh(final RefreshRequest request) {
        return loginService.refresh(request);
    }

    public void logout(final String accessToken, final String refreshToken) {
        loginService.logout(accessToken, refreshToken);
    }

    public void sendSms(final SendSmsRequest request) {
        String code = String.format("%06d",
                ThreadLocalRandom.current().nextInt(SMS_CODE_BOUND));
        String redisKey = CacheKeys.key(CacheKeys.SMS_CODE, request.getScene(), request.getPhone());
        redisTemplate.opsForValue().set(redisKey, code,
                java.time.Duration.ofMinutes(SMS_CODE_TTL_MINUTES));
        if (log.isInfoEnabled()) {
            log.info("[SMS mock] To: {} | Scene: {} | Code: {}",
                    request.getPhone(), request.getScene(), code);
        }
    }

    public TotpSetupResponse setupTotp(final Long userId) {
        return totpManagementService.setupTotp(userId);
    }

    public void enableTotp(final Long userId, final String code) {
        totpManagementService.enableTotp(userId, code);
    }

    public void disableTotp(final Long userId) {
        totpManagementService.disableTotp(userId);
    }

    public LoginResponse verifyTotpLogin(final Long userId, final String code) {
        return totpManagementService.verifyTotpLogin(userId, code);
    }
}