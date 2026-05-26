package com.zhiyu.common.feign;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthClientFallbackFactoryTest {

    private final AuthClientFallbackFactory factory = new AuthClientFallbackFactory();

    @Test
    void shouldCreateFallbackClient() {
        AuthClient client = factory.create(new RuntimeException("Connection refused"));
        assertThat(client).isNotNull();
    }

    @Test
    void shouldThrowOnGetUserById() {
        AuthClient client = factory.create(new RuntimeException("timeout"));
        assertThatThrownBy(() -> client.getUserById(1L))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Auth service unavailable");
    }

    @Test
    void shouldThrowOnGetUserByUsername() {
        AuthClient client = factory.create(new RuntimeException("timeout"));
        assertThatThrownBy(() -> client.getUserByUsername("test"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Auth service unavailable");
    }

    @Test
    void shouldThrowOnGetRoleById() {
        AuthClient client = factory.create(new RuntimeException("timeout"));
        assertThatThrownBy(() -> client.getRoleById(1L))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Auth service unavailable");
    }
}
