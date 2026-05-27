package com.zhiyu.common.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 缓存操作相关的 Spring 自动配置类。
 *
 * <p>根据 Classpath 上的类自动装配缓存实现：
 * 当存在 Caffeine 类时，注册本地内存缓存 {@link LocalCacheOperate}；
 * 当存在 Redisson 依赖时，注册分布式缓存 {@link RedissonCacheOperate}。</p>
 *
 * @author ZhiYu
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(Caffeine.class)
public class CacheAutoConfiguration {

    /**
     * 注册本地缓存操作器 Bean。
     *
     * @return 本地缓存操作器实例
     */
    @Bean
    @ConditionalOnMissingBean(name = "localCacheOperate")
    public LocalCacheOperate localCacheOperate() {
        return new LocalCacheOperate();
    }

    /**
     * 嵌套的 Redisson 缓存配置类。
     *
     * <p>采用静态内部类的方式，可有效避免在 Classpath 中缺少 RedissonClient 依赖时
     * 触发 ClassNotFoundException 的类加载失败隐患。
     * 外层类直接引用 RedissonClient 会导致 JVM 在评估条件注解之前就尝试加载该类参数，从而报错。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RedissonClient.class)
    static class RedissonConfiguration {

        /**
         * 注册 Redisson 分布式缓存操作器 Bean。
         *
         * @param redissonClient 自动注入的 Redisson 客户端
         * @return 分布式缓存操作器实例
         */
        @Bean
        @ConditionalOnMissingBean(RedissonCacheOperate.class)
        public RedissonCacheOperate redissonCacheOperate(final RedissonClient redissonClient) {
            return new RedissonCacheOperate(redissonClient);
        }
    }
}
