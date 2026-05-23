package com.zhiyu.ufp.auth.password;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

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
}
