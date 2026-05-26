package com.zhiyu.ufp.common.cache;

import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * SPI interface for cache operations.
 *
 * <p>Implementations provide a unified abstraction over different cache backends
 * (local in-memory Caffeine, distributed Redisson/Redis). Consumer modules
 * depend on this interface; the concrete implementation is provided at runtime
 * by a module that has the corresponding cache library on its classpath.</p>
 */
public interface ICacheOperate {

    /**
     * Returns the logical name of this cache provider (e.g. "local", "redisson").
     */
    String name();

    /**
     * Whether this cache provider should be treated as the default.
     * Override to return {@code true} when exactly one provider exists.
     */
    default boolean defaultCache() {
        return false;
    }

    /**
     * Returns the underlying native cache client (Caffeine {@code Cache} or
     * {@code RedissonClient}), useful for advanced operations not covered by
     * this SPI.
     */
    Object getNative();

    /**
     * Retrieve a value from the cache by key.
     *
     * @param name cache key
     * @param <T>  expected value type
     * @return the cached value, or {@code null} if absent
     */
    <T> T get(String name);

    /**
     * Store a value in the cache with the default TTL.
     */
    void set(String name, Object value);

    /**
     * Store a value with an explicit time-to-live.
     *
     * @param name       cache key
     * @param value      value to cache
     * @param timeToLive duration
     * @param timeUnit   time unit of {@code timeToLive}
     */
    void set(String name, Object value, long timeToLive, TimeUnit timeUnit);

    /**
     * Delete a single cache entry.
     */
    void delete(String name);

    /**
     * Delete all entries whose key matches the given pattern.
     * The pattern syntax is implementation-dependent (glob, regex, etc.).
     */
    void deletes(String pattern);

    /**
     * Delete multiple explicit keys.
     */
    void delete(String... keys);

    /**
     * Return all keys matching the given pattern.
     *
     * @param pattern pattern to match against cache keys
     * @param <T>     key type
     * @return collection of matching keys (may be empty, never null)
     */
    <T> Collection<T> keys(String pattern);

    /**
     * Obtain a distributed {@link Queue} view.
     * May throw {@link UnsupportedOperationException} for local caches.
     */
    Queue<?> getQueue(String name);

    /**
     * Obtain a distributed {@link Map} view.
     */
    <K, V> Map<K, V> getMap(String name);

    /**
     * Obtain a distributed {@link Lock} for the given key.
     *
     * @param name lock identifier
     * @param fair whether the lock should be fair
     * @return a {@link Lock} instance
     */
    Lock getLock(String name, boolean fair);
}
