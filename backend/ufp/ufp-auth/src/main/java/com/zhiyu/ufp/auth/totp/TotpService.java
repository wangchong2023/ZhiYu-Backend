package com.zhiyu.ufp.auth.totp;

import com.zhiyu.ufp.auth.entity.UserTotp;
import com.zhiyu.ufp.auth.mapper.UserTotpMapper;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TotpService {

    private static final String ISSUER = "ZhiYu";
    private static final int SECRET_BYTES = 20;
    private static final int TOTP_DIGITS = 6;
    private static final int TIME_STEP_SECONDS = 30;
    private static final int HMAC_SHA1_BYTES = 20;
    private static final String HMAC_SHA1 = "HmacSHA1";
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int[] BASE32_LOOKUP = buildBase32Lookup();
    private static final int BASE32_MASK = 0x1F;
    private static final int BITS_PER_BASE32_CHAR = 5;
    private static final int BITS_PER_BYTE = 8;
    private static final int LONG_BYTES = 8;
    private static final int MASK_BYTE = 0xFF;
    private static final int MASK_SIGN_BIT = 0x7F;
    private static final int TOTP_MODULUS = 1_000_000;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserTotpMapper userTotpMapper;

    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
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
        verifyCode(record.getSecret(), code);
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
        return verifyCode(record.getSecret(), code);
    }

    public boolean isTotpEnabled(final Long userId) {
        UserTotp record = userTotpMapper.selectById(userId);
        return record != null && record.getEnabled() != null && record.getEnabled() == UserTotp.ENABLED;
    }

    public String getSecret(final Long userId) {
        UserTotp record = userTotpMapper.selectById(userId);
        return record != null ? record.getSecret() : null;
    }

    // ── TOTP verification (RFC 6238) ──────────────────────────

    private boolean verifyCode(final String secret, final String code) {
        if (code == null || code.length() != TOTP_DIGITS) {
            return false;
        }
        try {
            byte[] key = base32Decode(secret);
            long counter = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
            String expected = generateTotp(key, counter);
            return code.equals(expected);
        } catch (Exception e) {
            log.warn("TOTP verification failed", e);
            return false;
        }
    }

    private String generateTotp(final byte[] key, final long counter) throws
            NoSuchAlgorithmException, InvalidKeyException {
        byte[] counterBytes = longToBytes(counter);

        Mac mac = Mac.getInstance(HMAC_SHA1);
        mac.init(new SecretKeySpec(key, HMAC_SHA1));
        byte[] hash = mac.doFinal(counterBytes);

        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & MASK_SIGN_BIT) << 24)
                | ((hash[offset + 1] & MASK_BYTE) << 16)
                | ((hash[offset + 2] & MASK_BYTE) << 8)
                | (hash[offset + 3] & MASK_BYTE);

        int otp = binary % TOTP_MODULUS;
        return String.format("%06d", otp);
    }

    private static byte[] longToBytes(final long value) {
        byte[] result = new byte[LONG_BYTES];
        for (int i = LONG_BYTES - 1; i >= 0; i--) {
            result[i] = (byte) (value & MASK_BYTE);
        }
        return result;
    }

    // ── Base32 ────────────────────────────────────────────────

    static String base32Encode(final byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsInBuffer = 0;

        for (byte b : data) {
            buffer = (buffer << BITS_PER_BYTE) | (b & MASK_BYTE);
            bitsInBuffer += BITS_PER_BYTE;
            while (bitsInBuffer >= BITS_PER_BASE32_CHAR) {
                bitsInBuffer -= BITS_PER_BASE32_CHAR;
                sb.append(BASE32_ALPHABET.charAt((buffer >> bitsInBuffer) & BASE32_MASK));
            }
        }
        if (bitsInBuffer > 0) {
            sb.append(BASE32_ALPHABET.charAt((buffer << (BITS_PER_BASE32_CHAR - bitsInBuffer)) & BASE32_MASK));
        }
        return sb.toString();
    }

    static byte[] base32Decode(final String input) {
        String normalized = input.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z2-7]", "");
        int outputLength = normalized.length() * BITS_PER_BASE32_CHAR / BITS_PER_BYTE;
        byte[] result = new byte[outputLength];
        int buffer = 0;
        int bitsInBuffer = 0;
        int resultIndex = 0;

        for (int i = 0; i < normalized.length(); i++) {
            int value = BASE32_LOOKUP[normalized.charAt(i)];
            if (value == -1) {
                continue;
            }
            buffer = (buffer << BITS_PER_BASE32_CHAR) | value;
            bitsInBuffer += BITS_PER_BASE32_CHAR;
            if (bitsInBuffer >= BITS_PER_BYTE) {
                bitsInBuffer -= BITS_PER_BYTE;
                result[resultIndex++] = (byte) ((buffer >> bitsInBuffer) & MASK_BYTE);
            }
        }
        return result;
    }

    private static int[] buildBase32Lookup() {
        int[] lookup = new int[128];
        for (int i = 0; i < 128; i++) {
            lookup[i] = -1;
        }
        for (int i = 0; i < BASE32_ALPHABET.length(); i++) {
            lookup[BASE32_ALPHABET.charAt(i)] = i;
        }
        lookup['0'] = lookup['O'];
        lookup['1'] = lookup['L'];
        lookup['8'] = lookup['B'];
        return lookup;
    }
}
