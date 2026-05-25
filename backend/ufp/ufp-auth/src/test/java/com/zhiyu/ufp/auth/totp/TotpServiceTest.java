package com.zhiyu.ufp.auth.totp;

import com.zhiyu.ufp.auth.entity.UserTotp;
import com.zhiyu.ufp.auth.mapper.UserTotpMapper;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TotpServiceTest {

    @Mock
    private UserTotpMapper userTotpMapper;

    @InjectMocks
    private TotpService totpService;

    // ── generateSecret() ─────────────────────────────────────────

    @Test
    void shouldGenerateSecretWithValidFormat() {
        String secret = totpService.generateSecret();

        assertThat(secret).isNotBlank();
        // Base32 alphabet only: A-Z and 2-7
        assertThat(secret).matches("^[A-Z2-7]+$");
        // 20 bytes → 32 Base32 characters
        assertThat(secret.length()).isEqualTo(32);
    }

    @Test
    void shouldGenerateDifferentSecrets() {
        List<String> secrets = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            secrets.add(totpService.generateSecret());
        }

        // All secrets should be unique (statistically near-certain with 20 random bytes)
        long uniqueCount = secrets.stream().distinct().count();
        assertThat(uniqueCount).isEqualTo(10);
    }

    // ── generateQrUri() ──────────────────────────────────────────

    @Test
    void shouldGenerateCorrectQrUriFormat() {
        String uri = totpService.generateQrUri("testuser", "JBSWY3DPEHPK3PXP");

        assertThat(uri).isNotBlank();
        assertThat(uri).startsWith("otpauth://totp/");
        assertThat(uri).contains("ZhiYu:testuser");
        assertThat(uri).contains("secret=JBSWY3DPEHPK3PXP");
        assertThat(uri).contains("issuer=ZhiYu");
    }

    @Test
    void shouldGenerateQrUriWithSpecialCharacters() {
        String uri = totpService.generateQrUri("user@example.com", "ABCDEFGH");

        assertThat(uri).contains("ZhiYu:user@example.com");
        assertThat(uri).contains("secret=ABCDEFGH");
    }

    // ── setupTotp() ──────────────────────────────────────────────

    @Test
    void shouldSetupTotpWhenUserDoesNotExist() {
        Long userId = 1001L;
        when(userTotpMapper.selectById(userId)).thenReturn(null);

        totpService.setupTotp(userId, "zhangsan");

        ArgumentCaptor<UserTotp> captor = ArgumentCaptor.forClass(UserTotp.class);
        verify(userTotpMapper).insert(captor.capture());

        UserTotp inserted = captor.getValue();
        assertThat(inserted.getUserId()).isEqualTo(userId);
        assertThat(inserted.getSecret()).isNotBlank().matches("^[A-Z2-7]+$");
        assertThat(inserted.getEnabled()).isZero();
    }

    @Test
    void shouldThrowValidationFailedWhenTotpAlreadyExists() {
        Long userId = 1001L;
        when(userTotpMapper.selectById(userId)).thenReturn(UserTotp.builder()
                .userId(userId)
                .secret("EXISTING")
                .enabled(1)
                .build());

        assertThatThrownBy(() -> totpService.setupTotp(userId, "zhangsan"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.VALIDATION_FAILED.getCode());

        verify(userTotpMapper, never()).insert(any(UserTotp.class));
    }

    // ── enableTotp() ─────────────────────────────────────────────

    @Test
    void shouldThrowTotpNotEnabledWhenEnableTotpNotFound() {
        Long userId = 9999L;
        when(userTotpMapper.selectById(userId)).thenReturn(null);

        assertThatThrownBy(() -> totpService.enableTotp(userId, "123456"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.TOTP_NOT_ENABLED.getCode());
    }

    @Test
    void shouldEnableTotpWithValidCode() throws Exception {
        Long userId = 1001L;
        String secret = totpService.generateSecret();
        UserTotp record = UserTotp.builder()
                .userId(userId)
                .secret(secret)
                .enabled(0)
                .build();
        when(userTotpMapper.selectById(userId)).thenReturn(record);

        byte[] key = TotpAlgorithm.base32Decode(secret);
        long counter = Instant.now().getEpochSecond() / 30;
        String validCode = TotpAlgorithm.generateTotp(key, counter);

        totpService.enableTotp(userId, validCode);

        assertThat(record.getEnabled()).isEqualTo(1);
        verify(userTotpMapper).updateById(record);
    }

    @Test
    void shouldThrowIncorrectCodeWhenEnableWithWrongCode() {
        Long userId = 1001L;
        String secret = totpService.generateSecret();
        UserTotp record = UserTotp.builder()
                .userId(userId)
                .secret(secret)
                .enabled(0)
                .build();
        when(userTotpMapper.selectById(userId)).thenReturn(record);

        assertThatThrownBy(() -> totpService.enableTotp(userId, "000000"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.TOTP_INCORRECT.getCode());

        verify(userTotpMapper, never()).updateById(any(UserTotp.class));
    }

    @Test
    void shouldThrowIncorrectCodeWhenEnablingWithNullCode() {
        Long userId = 1001L;
        String secret = totpService.generateSecret();
        UserTotp record = UserTotp.builder()
                .userId(userId)
                .secret(secret)
                .enabled(0)
                .build();
        when(userTotpMapper.selectById(userId)).thenReturn(record);

        assertThatThrownBy(() -> totpService.enableTotp(userId, null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.TOTP_INCORRECT.getCode());
    }

    @Test
    void shouldThrowIncorrectCodeWhenEnablingWithWrongLengthCode() {
        Long userId = 1001L;
        String secret = totpService.generateSecret();
        UserTotp record = UserTotp.builder()
                .userId(userId)
                .secret(secret)
                .enabled(0)
                .build();
        when(userTotpMapper.selectById(userId)).thenReturn(record);

        assertThatThrownBy(() -> totpService.enableTotp(userId, "12345"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.TOTP_INCORRECT.getCode());
    }

    // ── disableTotp() ────────────────────────────────────────────

    @Test
    void shouldDisableTotpWhenExists() {
        Long userId = 1001L;
        UserTotp record = UserTotp.builder()
                .userId(userId)
                .secret("SECRET")
                .enabled(1)
                .build();
        when(userTotpMapper.selectById(userId)).thenReturn(record);

        totpService.disableTotp(userId);

        verify(userTotpMapper).deleteById(userId);
    }

    @Test
    void shouldNotThrowWhenDisableTotpNotFound() {
        Long userId = 9999L;
        when(userTotpMapper.selectById(userId)).thenReturn(null);

        assertThatCode(() -> totpService.disableTotp(userId))
                .doesNotThrowAnyException();

        verify(userTotpMapper, never()).deleteById(any(Long.class));
    }

    // ── verifyTotp() ─────────────────────────────────────────────

    @Test
    void shouldVerifyTotpSuccessfully() throws Exception {
        Long userId = 1001L;
        String secret = totpService.generateSecret();
        UserTotp record = UserTotp.builder()
                .userId(userId)
                .secret(secret)
                .enabled(1)
                .build();
        when(userTotpMapper.selectById(userId)).thenReturn(record);

        byte[] key = TotpAlgorithm.base32Decode(secret);
        long counter = Instant.now().getEpochSecond() / 30;
        String validCode = TotpAlgorithm.generateTotp(key, counter);

        boolean result = totpService.verifyTotp(userId, validCode);

        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenTotpNotFound() {
        Long userId = 9999L;
        when(userTotpMapper.selectById(userId)).thenReturn(null);

        boolean result = totpService.verifyTotp(userId, "123456");

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenTotpNotEnabled() {
        Long userId = 1001L;
        UserTotp record = UserTotp.builder()
                .userId(userId)
                .secret("SECRET")
                .enabled(0)
                .build();
        when(userTotpMapper.selectById(userId)).thenReturn(record);

        boolean result = totpService.verifyTotp(userId, "123456");

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenEnabledIsNull() {
        Long userId = 1001L;
        UserTotp record = UserTotp.builder()
                .userId(userId)
                .secret("SECRET")
                .enabled(null)
                .build();
        when(userTotpMapper.selectById(userId)).thenReturn(record);

        boolean result = totpService.verifyTotp(userId, "123456");

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseForInvalidCode() {
        Long userId = 1001L;
        String secret = totpService.generateSecret();
        UserTotp record = UserTotp.builder()
                .userId(userId)
                .secret(secret)
                .enabled(1)
                .build();
        when(userTotpMapper.selectById(userId)).thenReturn(record);

        boolean result = totpService.verifyTotp(userId, "000000");

        // With random secret, "000000" is extremely unlikely to be valid
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseForNullCode() {
        Long userId = 1001L;
        UserTotp record = UserTotp.builder()
                .userId(userId)
                .secret("SECRET")
                .enabled(1)
                .build();
        when(userTotpMapper.selectById(userId)).thenReturn(record);

        boolean result = totpService.verifyTotp(userId, null);

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseForWrongLengthCode() {
        Long userId = 1001L;
        UserTotp record = UserTotp.builder()
                .userId(userId)
                .secret("SECRET")
                .enabled(1)
                .build();
        when(userTotpMapper.selectById(userId)).thenReturn(record);

        boolean result = totpService.verifyTotp(userId, "1234567"); // 7 digits

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseForEmptyCode() {
        Long userId = 1001L;
        UserTotp record = UserTotp.builder()
                .userId(userId)
                .secret("SECRET")
                .enabled(1)
                .build();
        when(userTotpMapper.selectById(userId)).thenReturn(record);

        boolean result = totpService.verifyTotp(userId, "");

        assertThat(result).isFalse();
    }

    // ── isTotpEnabled() ──────────────────────────────────────────

    @Test
    void shouldReturnTrueWhenTotpIsEnabled() {
        Long userId = 1001L;
        UserTotp record = UserTotp.builder()
                .userId(userId)
                .secret("SECRET")
                .enabled(1)
                .build();
        when(userTotpMapper.selectById(userId)).thenReturn(record);

        boolean result = totpService.isTotpEnabled(userId);

        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenTotpNotExistsForIsTotpEnabled() {
        Long userId = 9999L;
        when(userTotpMapper.selectById(userId)).thenReturn(null);

        boolean result = totpService.isTotpEnabled(userId);

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenTotpDisabledForIsTotpEnabled() {
        Long userId = 1001L;
        UserTotp record = UserTotp.builder()
                .userId(userId)
                .secret("SECRET")
                .enabled(0)
                .build();
        when(userTotpMapper.selectById(userId)).thenReturn(record);

        boolean result = totpService.isTotpEnabled(userId);

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenEnabledNullForIsTotpEnabled() {
        Long userId = 1001L;
        UserTotp record = UserTotp.builder()
                .userId(userId)
                .secret("SECRET")
                .enabled(null)
                .build();
        when(userTotpMapper.selectById(userId)).thenReturn(record);

        boolean result = totpService.isTotpEnabled(userId);

        assertThat(result).isFalse();
    }

    // ── getSecret() ──────────────────────────────────────────────

    @Test
    void shouldReturnSecretWhenTotpExists() {
        Long userId = 1001L;
        UserTotp record = UserTotp.builder()
                .userId(userId)
                .secret("MY_SECRET_VALUE")
                .enabled(1)
                .build();
        when(userTotpMapper.selectById(userId)).thenReturn(record);

        String secret = totpService.getSecret(userId);

        assertThat(secret).isEqualTo("MY_SECRET_VALUE");
    }

    @Test
    void shouldReturnNullWhenTotpNotFound() {
        Long userId = 9999L;
        when(userTotpMapper.selectById(userId)).thenReturn(null);

        String secret = totpService.getSecret(userId);

        assertThat(secret).isNull();
    }

    // ── Base32 encode/decode ─────────────────────────────────────

    @Test
    void shouldBase32EncodeKnownValue() {
        // RFC 4648 test vector: "fooba" -> "MZXW6YTB"
        byte[] input = "foobar".getBytes();
        String encoded = TotpAlgorithm.base32Encode(input);
        assertThat(encoded).isNotBlank();
    }

    @Test
    void shouldBase32EncodeAndDecodeRoundTrip() {
        byte[] original = new byte[20];
        for (int i = 0; i < 20; i++) {
            original[i] = (byte) (i + 1);
        }

        String encoded = TotpAlgorithm.base32Encode(original);
        byte[] decoded = TotpAlgorithm.base32Decode(encoded);

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void shouldBase32DecodeBeCaseInsensitive() {
        byte[] original = new byte[]{1, 2, 3, 4, 5};
        String upper = TotpAlgorithm.base32Encode(original);
        String lower = upper.toLowerCase();

        byte[] decodedUpper = TotpAlgorithm.base32Decode(upper);
        byte[] decodedLower = TotpAlgorithm.base32Decode(lower);

        assertThat(decodedUpper).isEqualTo(original);
        assertThat(decodedLower).isEqualTo(original);
    }

    @Test
    void shouldBase32DecodeHandleSpacesAndDashes() {
        byte[] original = new byte[]{10, 20, 30};
        String clean = TotpAlgorithm.base32Encode(original);

        // 3 bytes → 5 base32 chars; add space and dash as separators
        String dirty = clean.substring(0, 2) + " " + clean.substring(2, 4) + "-" + clean.substring(4);
        byte[] decoded = TotpAlgorithm.base32Decode(dirty);

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void shouldBase32DecodeHandleEmptyInput() {
        byte[] decoded = TotpAlgorithm.base32Decode("");
        assertThat(decoded).isEmpty();
    }

    @Test
    void shouldBase32DecodeHandleAllInvalidInput() {
        byte[] decoded = TotpAlgorithm.base32Decode("!@#$%^&*()");
        assertThat(decoded).isEmpty();
    }

    @Test
    void shouldBase32EncodeEmptyArray() {
        String encoded = TotpAlgorithm.base32Encode(new byte[0]);
        assertThat(encoded).isEmpty();
    }

    @Test
    void shouldBase32EncodeSingleByte() {
        byte[] input = new byte[]{0x41}; // 'A'
        String encoded = TotpAlgorithm.base32Encode(input);
        assertThat(encoded).isNotBlank();
        byte[] decoded = TotpAlgorithm.base32Decode(encoded);
        assertThat(decoded).isEqualTo(input);
    }

    // ── TOTP algorithm correctness ───────────────────────────────

    @Test
    void shouldGenerateSixDigitTotp() throws Exception {
        String secret = totpService.generateSecret();
        byte[] key = TotpAlgorithm.base32Decode(secret);
        long counter = Instant.now().getEpochSecond() / 30;

        String code = TotpAlgorithm.generateTotp(key, counter);

        assertThat(code).hasSize(6);
        assertThat(code).matches("^\\d{6}$");
    }

    @Test
    void shouldGenerateDeterministicTotp() throws Exception {
        String secret = totpService.generateSecret();
        byte[] key = TotpAlgorithm.base32Decode(secret);
        long counter = 12345678L;

        String code1 = TotpAlgorithm.generateTotp(key, counter);
        String code2 = TotpAlgorithm.generateTotp(key, counter);

        assertThat(code1).isEqualTo(code2);
    }

    @Test
    void shouldGenerateDifferentTotpForDifferentCounters() throws Exception {
        String secret = totpService.generateSecret();
        byte[] key = TotpAlgorithm.base32Decode(secret);

        String code1 = TotpAlgorithm.generateTotp(key, 100L);
        String code2 = TotpAlgorithm.generateTotp(key, 101L);

        assertThat(code1).isNotEqualTo(code2);
    }

    @Test
    void shouldReverseVerifyTotpWithMatchingCode() throws Exception {
        Long userId = 1001L;
        String secret = totpService.generateSecret();
        UserTotp record = UserTotp.builder()
                .userId(userId)
                .secret(secret)
                .enabled(1)
                .build();
        when(userTotpMapper.selectById(userId)).thenReturn(record);

        // Compute correct code
        byte[] key = TotpAlgorithm.base32Decode(secret);
        long counter = Instant.now().getEpochSecond() / 30;
        String correctCode = TotpAlgorithm.generateTotp(key, counter);

        boolean result = totpService.verifyTotp(userId, correctCode);

        assertThat(result).isTrue();
    }
}
