package com.zhiyu.common.tracing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TracingAutoConfigurationTest {

    @Test
    void shouldBeInstantiable() {
        TracingAutoConfiguration config = new TracingAutoConfiguration();
        assertThat(config).isNotNull();
    }
}
