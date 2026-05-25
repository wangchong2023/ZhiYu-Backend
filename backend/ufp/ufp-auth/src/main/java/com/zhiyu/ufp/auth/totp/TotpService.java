package com.zhiyu.ufp.auth.totp;

import com.zhiyu.ufp.auth.entity.UserTotp;
import com.zhiyu.ufp.auth.mapper.UserTotpMapper;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class TotpService {

    private static final String ISSUER = "ZhiYu";
    private static final int SECRET_BYTES = 20;
    private static final int TIME_STEP_SECONDS = 30;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserTotpMapper userTotpMapper;

    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return TotpAlgorithm.base32Encode(bytes);
    }

    public String generateQrUri(final String username, final String secret) {
        String label = ISSUER + ":" + username;
        return String.format("otpauth://totp/%s?secret=%s&issuer=%s", label, secret, ISSUER);
    }

    @Transactional(rollbackFor = Exception.class)
    public void setupTotp(final Long userId, final String username) {
        if (userTotpMapper.selectById(userId) != null) {
            throw new BizException(BizErrorCode.VALIDATION_FAILED);
        }
        String secret = generateSecret();
        UserTotp record = UserTotp.builder()
                .userId(userId)
                .secret(secret)
                .enabled(UserTotp.DISABLED)
                .build();
        userTotpMapper.insert(record);
    }

    @Transactional(rollbackFor = Exception.class)
    public void enableTotp(final Long userId, final String code) {
        UserTotp record = userTotpMapper.selectById(userId);
        if (record == null) {
            throw new BizException(BizErrorCode.TOTP_NOT_ENABLED);
        }
        if (!TotpAlgorithm.verifyCode(record.getSecret(), code)) {
            throw new BizException(BizErrorCode.TOTP_INCORRECT);
        }
        record.setEnabled(UserTotp.ENABLED);
        userTotpMapper.updateById(record);
    }

    @Transactional(rollbackFor = Exception.class)
    public void disableTotp(final Long userId) {
        UserTotp record = userTotpMapper.selectById(userId);
        if (record != null) {
            userTotpMapper.deleteById(userId);
        }
    }

    public boolean verifyTotp(final Long userId, final String code) {
        UserTotp record = userTotpMapper.selectById(userId);
        if (record == null || record.getEnabled() == null || record.getEnabled() != UserTotp.ENABLED) {
            return false;
        }
        return TotpAlgorithm.verifyCode(record.getSecret(), code);
    }

    public boolean isTotpEnabled(final Long userId) {
        UserTotp record = userTotpMapper.selectById(userId);
        return record != null && record.getEnabled() != null && record.getEnabled() == UserTotp.ENABLED;
    }

    public String getSecret(final Long userId) {
        UserTotp record = userTotpMapper.selectById(userId);
        return record != null ? record.getSecret() : null;
    }
}
