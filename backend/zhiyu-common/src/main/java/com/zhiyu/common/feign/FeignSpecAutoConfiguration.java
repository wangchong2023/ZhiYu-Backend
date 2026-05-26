package com.zhiyu.common.feign;

import feign.Retryer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(feign.Feign.class)
public class FeignSpecAutoConfiguration {

    private static final long FEIGN_RETRY_INITIAL_MS = 100L;
    private static final long FEIGN_RETRY_MAX_MS = 1000L;
    private static final int FEIGN_RETRY_MAX_ATTEMPTS = 3;

    @Bean
    public Retryer feignRetryer() {
        return new Retryer.Default(FEIGN_RETRY_INITIAL_MS,
                FEIGN_RETRY_MAX_MS, FEIGN_RETRY_MAX_ATTEMPTS);
    }

    @Bean
    public FeignErrorDecoder feignErrorDecoder() {
        return new FeignErrorDecoder();
    }
}
