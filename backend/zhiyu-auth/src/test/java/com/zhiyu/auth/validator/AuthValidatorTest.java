package com.zhiyu.auth.validator;

import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class AuthValidatorTest {

    @InjectMocks
    private AuthValidator validator;

    // ── validateUsername ──────────────────────────────────────

    @Test
    void shouldAcceptValidUsername() {
        assertThatCode(() -> validator.validateUsername("test_user"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptMinLengthUsername() {
        assertThatCode(() -> validator.validateUsername("abcd"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptMaxLengthUsername() {
        String username = "a".repeat(32);
        assertThatCode(() -> validator.validateUsername(username))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptUsernameWithNumbers() {
        assertThatCode(() -> validator.validateUsername("user123"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptUsernameWithUnderscores() {
        assertThatCode(() -> validator.validateUsername("my_user_name"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptUsernameWithMixedChars() {
        assertThatCode(() -> validator.validateUsername("Test_User_2024"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectNullUsername() {
        assertThatThrownBy(() -> validator.validateUsername(null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户名需 4-32 位");
    }

    @Test
    void shouldRejectTooShortUsername() {
        assertThatThrownBy(() -> validator.validateUsername("abc"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户名需 4-32 位");
    }

    @Test
    void shouldRejectTooLongUsername() {
        String longName = "a".repeat(33);
        assertThatThrownBy(() -> validator.validateUsername(longName))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户名需 4-32 位");
    }

    @Test
    void shouldRejectUsernameWithSpaces() {
        assertThatThrownBy(() -> validator.validateUsername("user name"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户名仅支持字母、数字、下划线");
    }

    @Test
    void shouldRejectUsernameWithSpecialChars() {
        assertThatThrownBy(() -> validator.validateUsername("user@name"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户名仅支持字母、数字、下划线");
    }

    @Test
    void shouldRejectUsernameWithHyphen() {
        assertThatThrownBy(() -> validator.validateUsername("user-name"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户名仅支持字母、数字、下划线");
    }

    @Test
    void shouldRejectUsernameWithChineseChars() {
        assertThatThrownBy(() -> validator.validateUsername("用户名字"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户名仅支持字母、数字、下划线");
    }

    // ── validatePassword ──────────────────────────────────────

    @Test
    void shouldAcceptValidPassword() {
        assertThatCode(() -> validator.validatePassword("Abcdefg1"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptMinLengthPassword() {
        assertThatCode(() -> validator.validatePassword("Abcdef1h"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptPasswordWithSpecialChars() {
        assertThatCode(() -> validator.validatePassword("Abcdef1!"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptComplexPassword() {
        assertThatCode(() -> validator.validatePassword("MyP@ssw0rd!2024#Secure"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectNullPassword() {
        assertThatThrownBy(() -> validator.validatePassword(null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("密码需 8-128 位");
    }

    @Test
    void shouldRejectTooShortPassword() {
        assertThatThrownBy(() -> validator.validatePassword("Abcde1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("密码需 8-128 位");
    }

    @Test
    void shouldRejectPasswordWithoutUppercase() {
        assertThatThrownBy(() -> validator.validatePassword("abcdefg1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("密码需包含大小写字母和数字");
    }

    @Test
    void shouldRejectPasswordWithoutLowercase() {
        assertThatThrownBy(() -> validator.validatePassword("ABCDEFG1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("密码需包含大小写字母和数字");
    }

    @Test
    void shouldRejectPasswordWithoutDigit() {
        assertThatThrownBy(() -> validator.validatePassword("Abcdefgh"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("密码需包含大小写字母和数字");
    }

    @Test
    void shouldRejectPasswordWithOnlyDigits() {
        assertThatThrownBy(() -> validator.validatePassword("12345678"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("密码需包含大小写字母和数字");
    }

    @Test
    void shouldRejectTooLongPassword() {
        String longPwd = "Aa1" + "x".repeat(126);
        assertThatThrownBy(() -> validator.validatePassword(longPwd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("密码需 8-128 位");
    }

    // ── validateEmail ─────────────────────────────────────────

    @Test
    void shouldAcceptValidEmail() {
        assertThatCode(() -> validator.validateEmail("test@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptEmailWithSubdomain() {
        assertThatCode(() -> validator.validateEmail("user@mail.example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectEmailWithPlusSign() {
        assertThatThrownBy(() -> validator.validateEmail("user+tag@example.com"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("邮箱格式不合法");
    }

    @Test
    void shouldAcceptEmailWithDotInLocal() {
        assertThatCode(() -> validator.validateEmail("first.last@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptEmailWithNumericDomain() {
        assertThatCode(() -> validator.validateEmail("user@123.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptEmailWithHyphen() {
        assertThatCode(() -> validator.validateEmail("user@my-domain.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectNullEmail() {
        assertThatThrownBy(() -> validator.validateEmail(null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("邮箱格式不合法");
    }

    @Test
    void shouldRejectEmailWithoutAtSign() {
        assertThatThrownBy(() -> validator.validateEmail("userexample.com"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("邮箱格式不合法");
    }

    @Test
    void shouldRejectEmailWithoutDomain() {
        assertThatThrownBy(() -> validator.validateEmail("user@"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("邮箱格式不合法");
    }

    @Test
    void shouldRejectEmailWithoutTLD() {
        assertThatThrownBy(() -> validator.validateEmail("user@example"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("邮箱格式不合法");
    }

    @Test
    void shouldRejectEmailWithSingleCharTLD() {
        assertThatThrownBy(() -> validator.validateEmail("user@example.c"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("邮箱格式不合法");
    }

    @Test
    void shouldRejectEmailWithoutLocalPart() {
        assertThatThrownBy(() -> validator.validateEmail("@example.com"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("邮箱格式不合法");
    }

    @Test
    void shouldRejectEmptyEmail() {
        assertThatThrownBy(() -> validator.validateEmail(""))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("邮箱格式不合法");
    }
}
