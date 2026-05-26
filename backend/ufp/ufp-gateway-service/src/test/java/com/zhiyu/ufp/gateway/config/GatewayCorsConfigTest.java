package com.zhiyu.ufp.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.reactive.CorsWebFilter;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayCorsConfigTest {

    private final GatewayCorsConfig config = new GatewayCorsConfig();

    @Test
    void shouldCreateCorsWebFilter() {
        CorsWebFilter filter = config.corsWebFilter();
        assertThat(filter).isNotNull();
    }
}
