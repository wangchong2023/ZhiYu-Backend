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
 * Redisson-based distributed implementation of {@link ICacheOperate}.
 *
 * <p>Delegates all cache operations to a {@link RedissonClient}. Requires
 * a configured {@code RedissonClient} bean in the application context.</p>
 */
@RequiredArgsConstructor
public class RedissonCacheOperate implements ICacheOperate {

    private final RedissonClient redissonClient;

    @Override
    public String name() {
        return "redisson";
    }

    @Override
    public Object getNative() {
        return redissonClient;
    }

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
