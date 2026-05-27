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
 * 基于 Caffeine 实现的本地内存缓存操作类。
 *
 * <p>实现 {@link ICacheOperate} 接口。
 * 默认缓存的过期时间为 30 分钟，最大容量为 10,000 条记录。
 * 自定义生存时间（TTL）的缓存项将被分类存储在对应生存时间的辅助缓存中。</p>
 *
 * @author ZhiYu
 * @since 1.0.0
 */
public class LocalCacheOperate implements ICacheOperate {

    private static final int DEFAULT_MAX_SIZE = 10_000;
    private static final long DEFAULT_TTL_MINUTES = 30;

    /** 默认的本地缓存实例（30分钟过期） */
    private final Cache<Object, Object> defaultCache;
    /** 针对特定TTL创建的辅助本地缓存映射表 */
    private final ConcurrentMap<String, Cache<Object, Object>> timedCaches;
    /** 键与对应缓存实例的映射，用于快速查找和删除 */
    private final ConcurrentMap<String, Cache<Object, Object>> keyStore;

    /**
     * 初始化本地缓存操作器，设置默认缓存配置。
     */
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

    /**
     * 根据缓存键获取缓存值。
     *
     * <p>由于底层接口返回泛型 {@code T}，而底层容器存储为 {@code Object}。
     * 在泛型擦除机制下，此处强制转型无法由编译器进行类型安全检查，
     * 故使用 {@code @SuppressWarnings("unchecked")} 抑制未检查强转警告。
     * 此处的转型安全由调用方对存入与读取的数据类型一致性进行保证。</p>
     *
     * @param name 缓存键
     * @param <T> 期望的值类型
     * @return 缓存的值，若不存在则返回 null
     */
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

    /**
     * 根据模式匹配获取所有缓存键。
     *
     * <p>此处将匹配出的 {@code Set<String>} 强转为 {@code Collection<T>} 返回。
     * 由于接口返回泛型集合，而本地缓存的键在逻辑上均为 {@code String}，
     * 此处强转安全，因此使用 {@code @SuppressWarnings("unchecked")} 抑制警告。</p>
     *
     * @param pattern 键匹配模式（例如 "user:*"）
     * @param <T> 键类型
     * @return 匹配的键集合
     */
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

    /**
     * 获取分布式或本地的 Map 缓存视图。
     *
     * <p>将缓存的 map 视图进行类型强转，由于底层存储均为 Map 结构，
     * 强转在逻辑上安全，因此使用 {@code @SuppressWarnings("unchecked")} 抑制警告。</p>
     *
     * @param name 缓存名称
     * @param <K> 键类型
     * @param <V> 值类型
     * @return Map 缓存视图
     */
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
