package com.zhiyu.ufp.auth.password;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordServiceTest {

    private final PasswordService service = new PasswordService(12);

    @Test
    void shouldHashAndVerifyPassword() {
        String hash = service.hash("MySecret123");
        assertThat(hash).isNotBlank().startsWith("$2");
        assertThat(service.verify("MySecret123", hash)).isTrue();
    }

    @Test
    void shouldRejectWrongPassword() {
        String hash = service.hash("CorrectPass1");
        assertThat(service.verify("WrongPass1", hash)).isFalse();
    }

    @Test
    void shouldGenerateDifferentHashesForSamePassword() {
        String h1 = service.hash("SamePass1");
        String h2 = service.hash("SamePass1");
        assertThat(h1).isNotEqualTo(h2);
        assertThat(service.verify("SamePass1", h1)).isTrue();
        assertThat(service.verify("SamePass1", h2)).isTrue();
    }

    // ── default constructor ───────────────────────────────────────

    @Test
    void shouldWorkWithDefaultConstructor() {
        PasswordService defaultService = new PasswordService();

        String hash = defaultService.hash("TestPassword123");
        assertThat(hash).isNotBlank().startsWith("$2");
        assertThat(defaultService.verify("TestPassword123", hash)).isTrue();
        assertThat(defaultService.verify("WrongPassword", hash)).isFalse();
    }

    @Test
    void shouldThrowOnNullPasswordHash() {
        assertThatThrownBy(() -> service.hash(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectEmptyPassword() {
        String hash = service.hash("");
        assertThat(service.verify("", hash)).isTrue();
        assertThat(service.verify("something", hash)).isFalse();
    }

    @Test
    void shouldThrowOnNullVerify() {
        String hash = service.hash("MyPassword");
        assertThatThrownBy(() -> service.verify(null, hash))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
