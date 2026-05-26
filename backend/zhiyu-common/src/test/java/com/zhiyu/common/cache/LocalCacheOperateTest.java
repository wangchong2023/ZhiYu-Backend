package com.zhiyu.common.cache;

import com.zhiyu.ufp.common.cache.ICacheOperate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalCacheOperateTest {

    private LocalCacheOperate cache;

    @BeforeEach
    void setUp() {
        cache = new LocalCacheOperate();
    }

    @Test
    void shouldReturnLocalName() {
        assertThat(cache.name()).isEqualTo("local");
    }

    @Test
    void shouldReturnDefaultCacheAsNative() {
        assertThat(cache.getNative()).isNotNull();
    }

    @Test
    void shouldSetAndGetValue() {
        cache.set("key1", "value1");
        assertThat(cache.<String>get("key1")).isEqualTo("value1");
    }

    @Test
    void shouldReturnNullForMissingKey() {
        assertThat(cache.<String>get("nonexistent")).isNull();
    }

    @Test
    void shouldSetWithTTL() {
        cache.set("ttl-key", "ttl-value", 60, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(cache.<String>get("ttl-key")).isEqualTo("ttl-value");
    }

    @Test
    void shouldDeleteKey() {
        cache.set("del-key", "del-value");
        cache.delete("del-key");
        assertThat(cache.<String>get("del-key")).isNull();
    }

    @Test
    void shouldDeleteMultipleKeys() {
        cache.set("a", 1);
        cache.set("b", 2);
        cache.delete("a", "b");
        assertThat(cache.<Integer>get("a")).isNull();
        assertThat(cache.<Integer>get("b")).isNull();
    }

    @Test
    void shouldDeleteByPattern() {
        cache.set("user:1", "x");
        cache.set("user:2", "y");
        cache.set("admin:1", "z");
        cache.deletes("user:*");
        assertThat(cache.<String>get("user:1")).isNull();
        assertThat(cache.<String>get("user:2")).isNull();
        assertThat(cache.<String>get("admin:1")).isEqualTo("z");
    }

    @Test
    void shouldGetKeysByPattern() {
        cache.set("user:1", "a");
        cache.set("user:2", "b");
        cache.set("admin:3", "c");
        Collection<String> keys = cache.keys("user:*");
        assertThat(keys).containsExactlyInAnyOrder("user:1", "user:2");
    }

    @Test
    void shouldGetMap() {
        cache.set("map-key", "map-value");
        Map<String, Object> map = cache.getMap("map-key");
        assertThat(map).isNotNull();
    }

    @Test
    void shouldNotSupportQueues() {
        assertThatThrownBy(() -> cache.getQueue("test"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldNotSupportLocks() {
        assertThatThrownBy(() -> cache.getLock("test", false))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
