package com.zhiyu.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class RestClientConfigTest {

    private final RestClientConfig config = new RestClientConfig();

    @Test
    void shouldCreateRestTemplate() {
        RestTemplate restTemplate = config.restTemplate();

        assertThat(restTemplate).isNotNull();
    }

    @Test
    void shouldCreateNewInstanceOnEachCall() {
        RestTemplate t1 = config.restTemplate();
        RestTemplate t2 = config.restTemplate();

        assertThat(t1).isNotSameAs(t2);
    }
}
