package com.zhiyu.ufp.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayHealthIndicatorTest {

    private final GatewayHealthIndicator indicator = new GatewayHealthIndicator();

    @Test
    void shouldReturnUpStatus() {
        Health health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void shouldIncludeGatewayType() {
        Health health = indicator.health();
        assertThat(health.getDetails()).containsEntry("type", "gateway");
    }

    @Test
    void shouldIncludeReactiveNettyMode() {
        Health health = indicator.health();
        assertThat(health.getDetails()).containsEntry("mode", "reactive-netty");
    }
}
