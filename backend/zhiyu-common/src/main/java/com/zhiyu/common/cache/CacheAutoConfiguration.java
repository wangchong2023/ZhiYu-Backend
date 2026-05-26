package com.zhiyu.common.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for cache operation beans.
 *
 * <p>Registers {@link LocalCacheOperate} when Caffeine is on the classpath
 * and {@link RedissonCacheOperate} when Redisson is available.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(Caffeine.class)
public class CacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "localCacheOperate")
    public LocalCacheOperate localCacheOperate() {
        return new LocalCacheOperate();
    }

    @Bean
    @ConditionalOnClass(RedissonClient.class)
    @ConditionalOnMissingBean(RedissonCacheOperate.class)
    public RedissonCacheOperate redissonCacheOperate(final RedissonClient redissonClient) {
        return new RedissonCacheOperate(redissonClient);
    }
}
