package com.zhiyu.ufp.common.cache;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ICacheOperateTest {

    @Test
    void shouldDefaultCacheReturnFalse() {
        ICacheOperate cache = new StubCacheOperate();
        assertThat(cache.defaultCache()).isFalse();
    }

    @Test
    void shouldReturnConfiguredName() {
        ICacheOperate cache = new StubCacheOperate();
        assertThat(cache.name()).isEqualTo("stub");
    }

    @Test
    void shouldReturnNativeClient() {
        StubCacheOperate cache = new StubCacheOperate();
        assertThat(cache.getNative()).isEqualTo("stub-native");
    }

    @Test
    void shouldSetAndGetValue() {
        StubCacheOperate cache = new StubCacheOperate();
        cache.set("key1", "value1");
        assertThat(cache.<String>get("key1")).isEqualTo("value1");
    }

    @Test
    void shouldSetWithTTL() {
        StubCacheOperate cache = new StubCacheOperate();
        cache.set("key2", "value2", 60, TimeUnit.SECONDS);
        assertThat(cache.<String>get("key2")).isEqualTo("value2");
    }

    @Test
    void shouldDeleteKey() {
        StubCacheOperate cache = new StubCacheOperate();
        cache.set("key3", "value3");
        cache.delete("key3");
        assertThat(cache.<String>get("key3")).isNull();
    }

    @Test
    void shouldDeleteMultipleKeys() {
        StubCacheOperate cache = new StubCacheOperate();
        cache.set("a", 1);
        cache.set("b", 2);
        cache.delete("a", "b");
        assertThat(cache.<Integer>get("a")).isNull();
        assertThat(cache.<Integer>get("b")).isNull();
    }

    @Test
    void shouldDeleteByPattern() {
        StubCacheOperate cache = new StubCacheOperate();
        cache.set("test:1", "x");
        cache.set("test:2", "y");
        cache.set("other:3", "z");
        cache.deletes("test:*");
        assertThat(cache.<String>get("test:1")).isNull();
        assertThat(cache.<String>get("test:2")).isNull();
        assertThat(cache.<String>get("other:3")).isEqualTo("z");
    }

    @Test
    void shouldGetKeysByPattern() {
        StubCacheOperate cache = new StubCacheOperate();
        cache.set("user:1", "a");
        cache.set("user:2", "b");
        Collection<String> keys = cache.keys("user:*");
        assertThat(keys).containsExactlyInAnyOrder("user:1", "user:2");
    }

    @Test
    void shouldGetMap() {
        StubCacheOperate cache = new StubCacheOperate();
        cache.set("m1", "v1");
        Map<String, Object> map = cache.getMap("m1");
        assertThat(map).isNotNull();
        assertThat(map.get("m1")).isEqualTo("v1");
    }

    static class StubCacheOperate implements ICacheOperate {
        private final java.util.concurrent.ConcurrentHashMap<String, Object> store = new java.util.concurrent.ConcurrentHashMap<>();
        private final java.util.concurrent.ConcurrentHashMap<String, Long> ttls = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public String name() {
            return "stub";
        }

        @Override
        public Object getNative() {
            return "stub-native";
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T get(String name) {
            Long ttl = ttls.get(name);
            if (ttl != null && System.currentTimeMillis() > ttl) {
                store.remove(name);
                ttls.remove(name);
                return null;
            }
            return (T) store.get(name);
        }

        @Override
        public void set(String name, Object value) {
            store.put(name, value);
        }

        @Override
        public void set(String name, Object value, long timeToLive, TimeUnit timeUnit) {
            store.put(name, value);
            ttls.put(name, System.currentTimeMillis() + timeUnit.toMillis(timeToLive));
        }

        @Override
        public void delete(String name) {
            store.remove(name);
            ttls.remove(name);
        }

        @Override
        public void deletes(String pattern) {
            String regex = pattern.replace("*", ".*");
            store.keySet().removeIf(k -> k.matches(regex));
        }

        @Override
        public void delete(String... keys) {
            for (String k : keys) {
                store.remove(k);
                ttls.remove(k);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Collection<T> keys(String pattern) {
            String regex = pattern.replace("*", ".*");
            return (Collection<T>) store.keySet().stream()
                    .filter(k -> k instanceof String && ((String) k).matches(regex))
                    .toList();
        }

        @Override
        public Queue<?> getQueue(String name) {
            return new java.util.concurrent.ConcurrentLinkedQueue<>();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <K, V> Map<K, V> getMap(String name) {
            return (Map<K, V>) store;
        }

        @Override
        public Lock getLock(String name, boolean fair) {
            return new java.util.concurrent.locks.ReentrantLock(fair);
        }
    }
}
