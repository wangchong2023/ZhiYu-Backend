package com.zhiyu.auth.converter;

import com.zhiyu.auth.dto.RegisterRequest;
import com.zhiyu.ufp.auth.entity.AuthUser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthConverterTest {

    @Test
    void shouldMapRegisterRequestToAuthUser() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPassword("Abc12345");
        request.setEmail("test@example.com");

        AuthUser user = AuthConverter.INSTANCE.toEntity(request);

        assertThat(user.getAuthUserUsername()).isEqualTo("testuser");
        assertThat(user.getAuthUserNick()).isEqualTo("testuser");
        assertThat(user.getAuthUserMail()).isEqualTo("test@example.com");
        assertThat(user.getAuthUserUsernameLoginEnable()).isEqualTo(1);
        assertThat(user.getAuthUserMailVerified()).isEqualTo(0);
        assertThat(user.getAuthUserMailLoginEnable()).isEqualTo(0);
        assertThat(user.getAuthUserEnable()).isEqualTo(1);
        assertThat(user.getAuthUserDeleted()).isEqualTo(0);
        assertThat(user.getAuthUserScope()).isEqualTo("openid");
        assertThat(user.getCreatedUser()).isEqualTo("SYSTEM");
    }

    @Test
    void shouldSetCreatedTimeOnMapping() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("Abc12345");
        request.setEmail("new@example.com");

        AuthUser user = AuthConverter.INSTANCE.toEntity(request);

        assertThat(user.getCreatedTime()).isNotNull();
        assertThat(user.getCreatedTime()).isBeforeOrEqualTo(java.time.LocalDateTime.now());
    }

    @Test
    void shouldIgnoreAuthUserId() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("user1");
        request.setPassword("Abc12345");
        request.setEmail("user1@example.com");

        AuthUser user = AuthConverter.INSTANCE.toEntity(request);

        assertThat(user.getAuthUserId()).isNull();
    }

    @Test
    void shouldIgnoreAuthUserCode() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("user2");
        request.setPassword("Abc12345");
        request.setEmail("user2@example.com");

        AuthUser user = AuthConverter.INSTANCE.toEntity(request);

        assertThat(user.getAuthUserCode()).isNull();
    }

    @Test
    void shouldIgnorePasswordFields() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("user3");
        request.setPassword("Abc12345");
        request.setEmail("user3@example.com");

        AuthUser user = AuthConverter.INSTANCE.toEntity(request);

        assertThat(user.getAuthUserPassword()).isNull();
        assertThat(user.getAuthUserPasswordSalt()).isNull();
        assertThat(user.getAuthUserPasswordExpire()).isNull();
        assertThat(user.getAuthUserPasswordHistory()).isNull();
    }

    @Test
    void shouldIgnoreMobileFields() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("user4");
        request.setPassword("Abc12345");
        request.setEmail("user4@example.com");

        AuthUser user = AuthConverter.INSTANCE.toEntity(request);

        assertThat(user.getAuthUserMobile()).isNull();
        assertThat(user.getAuthUserMobileVerified()).isNull();
        assertThat(user.getAuthUserMobileLoginEnable()).isNull();
    }

    @Test
    void shouldIgnoreEnableExpire() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("user5");
        request.setPassword("Abc12345");
        request.setEmail("user5@example.com");

        AuthUser user = AuthConverter.INSTANCE.toEntity(request);

        assertThat(user.getAuthUserEnableExpire()).isNull();
    }

    @Test
    void shouldIgnoreUpdatedFields() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("user6");
        request.setPassword("Abc12345");
        request.setEmail("user6@example.com");

        AuthUser user = AuthConverter.INSTANCE.toEntity(request);

        assertThat(user.getUpdatedUser()).isNull();
        assertThat(user.getUpdatedTime()).isNull();
    }

    @Test
    void shouldMapUsernameWithUnderscores() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("test_user_2024");
        request.setPassword("Abc12345");
        request.setEmail("test@example.com");

        AuthUser user = AuthConverter.INSTANCE.toEntity(request);

        assertThat(user.getAuthUserUsername()).isEqualTo("test_user_2024");
        assertThat(user.getAuthUserNick()).isEqualTo("test_user_2024");
    }

    @Test
    void shouldSetDefaultConstantsCorrectly() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("defaults");
        request.setPassword("Abc12345");
        request.setEmail("defaults@example.com");

        AuthUser user = AuthConverter.INSTANCE.toEntity(request);

        // Default int constants should be boxed to Integer 0 or 1
        assertThat(user.getAuthUserEnable()).isEqualTo(1);
        assertThat(user.getAuthUserDeleted()).isEqualTo(0);
        assertThat(user.getAuthUserMailVerified()).isEqualTo(0);
        assertThat(user.getAuthUserUsernameLoginEnable()).isEqualTo(1);
        assertThat(user.getAuthUserMailLoginEnable()).isEqualTo(0);
    }

    @Test
    void shouldHaveSingletonInstance() {
        assertThat(AuthConverter.INSTANCE).isNotNull();
        // Call again to verify it's the same
        AuthUser u1 = AuthConverter.INSTANCE.toEntity(createRequest("a", "Abc12345", "a@b.com"));
        AuthUser u2 = AuthConverter.INSTANCE.toEntity(createRequest("b", "Abc12345", "b@b.com"));
        assertThat(u1.getAuthUserUsername()).isEqualTo("a");
        assertThat(u2.getAuthUserUsername()).isEqualTo("b");
    }

    private RegisterRequest createRequest(String username, String password, String email) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setPassword(password);
        req.setEmail(email);
        return req;
    }
}
