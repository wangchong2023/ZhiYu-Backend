package com.zhiyu.ufp.common.spi;

/**
 * SPI for pluggable cache backends.
 * Implement and register as a Spring bean to provide
 * custom cache operations.
 */
public interface CacheProvider {

    /** Unique name for this cache provider. */
    String name();

    /** Whether this is the default cache provider. */
    default boolean isDefault() {
        return false;
    }

    /** Get a cached value by key. */
    String get(String key);

    /** Set a cached value with TTL in seconds. */
    void set(String key, String value, long ttlSeconds);

    /** Delete a cached key. */
    void delete(String key);
}
