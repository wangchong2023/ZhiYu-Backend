package com.zhiyu.common.cache;

import com.zhiyu.ufp.common.cache.ICacheOperate;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;

import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * 基于 Redisson 实现的分布式缓存操作类。
 *
 * <p>将所有的缓存操作委托给 {@link RedissonClient} 实例进行处理。
 * 需要在 Spring 应用上下文中提前配置并注入 {@code RedissonClient} Bean。</p>
 *
 * @author ZhiYu
 * @since 1.0.0
 */
@RequiredArgsConstructor
public class RedissonCacheOperate implements ICacheOperate {

    /** Redisson 客户端实例 */
    private final RedissonClient redissonClient;

    @Override
    public String name() {
        return "redisson";
    }

    @Override
    public Object getNative() {
        return redissonClient;
    }

    /**
     * 根据缓存键获取缓存值。
     *
     * <p>由于 RedissonClient 获取的值为 {@code Object} 强转为期望的泛型 {@code T}。
     * 在泛型擦除机制下无法通过编译器进行类型安全校验，
     * 故使用 {@code @SuppressWarnings("unchecked")} 抑制警告。
     * 实际类型一致性由调用方进行维护。</p>
     *
     * @param name 缓存键
     * @param <T> 期望的值类型
     * @return 缓存的值，若不存在则返回 null
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(final String name) {
        return (T) redissonClient.getBucket(name).get();
    }

    @Override
    public void set(final String name, final Object value) {
        redissonClient.getBucket(name).set(value);
    }

    @Override
    public void set(final String name, final Object value,
                    final long timeToLive, final TimeUnit timeUnit) {
        redissonClient.getBucket(name).set(value, timeToLive, timeUnit);
    }

    @Override
    public void delete(final String name) {
        redissonClient.getBucket(name).delete();
    }

    @Override
    public void deletes(final String pattern) {
        redissonClient.getKeys().deleteByPattern(pattern);
    }

    @Override
    public void delete(final String... keys) {
        redissonClient.getKeys().delete(keys);
    }

    /**
     * 根据模式匹配获取所有缓存键。
     *
     * <p>由于 Redisson 返回的键集合需要强转为 {@code Collection<T>} 返回，
     * 在泛型擦除机制下无法通过编译器进行类型安全校验，
     * 故使用 {@code @SuppressWarnings("unchecked")} 抑制警告。</p>
     *
     * @param pattern 键匹配模式
     * @param <T> 键的泛型类型
     * @return 匹配的键集合
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> Collection<T> keys(final String pattern) {
        return (Collection<T>) redissonClient.getKeys().getKeysByPattern(pattern);
    }

    @Override
    public Queue<?> getQueue(final String name) {
        return redissonClient.getQueue(name);
    }

    @Override
    public <K, V> Map<K, V> getMap(final String name) {
        return redissonClient.getMap(name);
    }

    @Override
    public Lock getLock(final String name, final boolean fair) {
        if (fair) {
            return redissonClient.getFairLock(name);
        }
        return redissonClient.getLock(name);
    }
}
