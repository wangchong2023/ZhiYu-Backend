package com.zhiyu.common.feign;

import feign.Retryer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeignSpecAutoConfigurationTest {

    private final FeignSpecAutoConfiguration config = new FeignSpecAutoConfiguration();

    @Test
    void shouldCreateRetryerWithCorrectConfiguration() {
        Retryer retryer = config.feignRetryer();
        assertThat(retryer).isNotNull();
        assertThat(retryer).isInstanceOf(Retryer.Default.class);
    }

    @Test
    void shouldCreateFeignErrorDecoder() {
        FeignErrorDecoder decoder = config.feignErrorDecoder();
        assertThat(decoder).isNotNull();
    }
}
