package com.zhiyu.common.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zhiyu.ufp.common.cache.ICacheOperate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.regex.Pattern;

/**
 * Caffeine-based local in-memory implementation of {@link ICacheOperate}.
 *
 * <p>Uses a default cache with 30-minute expiry and 10 000 max entries.
 * Entries with custom TTL are stored in per-TTL auxiliary caches.</p>
 */
public class LocalCacheOperate implements ICacheOperate {

    private static final int DEFAULT_MAX_SIZE = 10_000;
    private static final long DEFAULT_TTL_MINUTES = 30;

    private final Cache<Object, Object> defaultCache;
    private final ConcurrentMap<String, Cache<Object, Object>> timedCaches;
    private final ConcurrentMap<String, Cache<Object, Object>> keyStore;

    public LocalCacheOperate() {
        this.defaultCache = Caffeine.newBuilder()
                .expireAfterWrite(DEFAULT_TTL_MINUTES, TimeUnit.MINUTES)
                .maximumSize(DEFAULT_MAX_SIZE)
                .build();
        this.timedCaches = new ConcurrentHashMap<>();
        this.keyStore = new ConcurrentHashMap<>();
    }

    @Override
    public String name() {
        return "local";
    }

    @Override
    public Object getNative() {
        return defaultCache;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(final String name) {
        Cache<Object, Object> cache = keyStore.get(name);
        if (cache == null) {
            return null;
        }
        T value = (T) cache.getIfPresent(name);
        if (value == null) {
            keyStore.remove(name);
        }
        return value;
    }

    @Override
    public void set(final String name, final Object value) {
        defaultCache.put(name, value);
        keyStore.put(name, defaultCache);
    }

    @Override
    public void set(final String name, final Object value,
                    final long timeToLive, final TimeUnit timeUnit) {
        String cacheKey = timeToLive + ":" + timeUnit.name();
        Cache<Object, Object> timed = timedCaches.computeIfAbsent(cacheKey, k ->
                Caffeine.newBuilder()
                        .expireAfterWrite(timeToLive, timeUnit)
                        .maximumSize(DEFAULT_MAX_SIZE)
                        .build());
        timed.put(name, value);
        keyStore.put(name, timed);
    }

    @Override
    public void delete(final String name) {
        Cache<Object, Object> cache = keyStore.remove(name);
        if (cache != null) {
            cache.invalidate(name);
        }
    }

    @Override
    public void deletes(final String pattern) {
        String regex = pattern.replace("*", ".*");
        Pattern p = Pattern.compile(regex);

        List<String> toDelete = new ArrayList<>();
        for (Object key : defaultCache.asMap().keySet()) {
            if (key instanceof String s && p.matcher(s).matches()) {
                toDelete.add(s);
            }
        }
        for (Cache<Object, Object> tc : timedCaches.values()) {
            for (Object key : tc.asMap().keySet()) {
                if (key instanceof String s && p.matcher(s).matches()) {
                    toDelete.add(s);
                }
            }
        }

        for (String key : toDelete) {
            Cache<Object, Object> cache = keyStore.remove(key);
            if (cache != null) {
                cache.invalidate(key);
            }
        }
    }

    @Override
    public void delete(final String... keys) {
        for (String key : keys) {
            Cache<Object, Object> cache = keyStore.remove(key);
            if (cache != null) {
                cache.invalidate(key);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Collection<T> keys(final String pattern) {
        String regex = pattern.replace("*", ".*");
        Pattern p = Pattern.compile(regex);

        Set<String> matching = new HashSet<>();
        for (Object key : defaultCache.asMap().keySet()) {
            if (key instanceof String s && p.matcher(s).matches()) {
                matching.add(s);
            }
        }
        for (Cache<Object, Object> tc : timedCaches.values()) {
            for (Object key : tc.asMap().keySet()) {
                if (key instanceof String s && p.matcher(s).matches()) {
                    matching.add(s);
                }
            }
        }
        return (Collection<T>) matching;
    }

    @Override
    public Queue<?> getQueue(final String name) {
        throw new UnsupportedOperationException(
                "Local Caffeine cache does not support distributed queues");
    }

    @SuppressWarnings("unchecked")
    @Override
    public <K, V> Map<K, V> getMap(final String name) {
        Cache<Object, Object> cache = keyStore.get(name);
        if (cache != null) {
            return (Map<K, V>) cache.asMap();
        }
        return (Map<K, V>) defaultCache.asMap();
    }

    @Override
    public Lock getLock(final String name, final boolean fair) {
        throw new UnsupportedOperationException(
                "Local Caffeine cache does not support distributed locks");
    }
}
