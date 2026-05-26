package com.zhiyu.common.cache;

import com.zhiyu.ufp.common.cache.ICacheOperate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RQueue;
import org.redisson.api.RedissonClient;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedissonCacheOperateTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<Object> bucket;

    @Mock
    private RKeys rKeys;

    @Mock
    private RQueue<Object> queue;

    @Mock
    private RMap<Object, Object> rMap;

    @Mock
    private RLock lock;

    private RedissonCacheOperate cache;

    @BeforeEach
    void setUp() {
        cache = new RedissonCacheOperate(redissonClient);
    }

    @Test
    void shouldReturnRedissonName() {
        assertThat(cache.name()).isEqualTo("redisson");
    }

    @Test
    void shouldReturnRedissonClientAsNative() {
        assertThat(cache.getNative()).isEqualTo(redissonClient);
    }

    @Test
    void shouldGetValue() {
        when(redissonClient.getBucket("key1")).thenReturn(bucket);
        when(bucket.get()).thenReturn("value1");
        assertThat(cache.<String>get("key1")).isEqualTo("value1");
    }

    @Test
    void shouldSetValue() {
        when(redissonClient.getBucket("key1")).thenReturn(bucket);
        cache.set("key1", "value1");
        verify(bucket).set("value1");
    }

    @Test
    void shouldSetValueWithTTL() {
        when(redissonClient.getBucket("key1")).thenReturn(bucket);
        cache.set("key1", "value1", 60, TimeUnit.SECONDS);
        verify(bucket).set("value1", 60, TimeUnit.SECONDS);
    }

    @Test
    void shouldDeleteKey() {
        when(redissonClient.getBucket("key1")).thenReturn(bucket);
        cache.delete("key1");
        verify(bucket).delete();
    }

    @Test
    void shouldDeleteByPattern() {
        when(redissonClient.getKeys()).thenReturn(rKeys);
        cache.deletes("user:*");
        verify(rKeys).deleteByPattern("user:*");
    }

    @Test
    void shouldDeleteMultipleKeys() {
        when(redissonClient.getKeys()).thenReturn(rKeys);
        cache.delete("a", "b");
        verify(rKeys).delete("a", "b");
    }

    @Test
    void shouldGetKeysByPattern() {
        when(redissonClient.getKeys()).thenReturn(rKeys);
        when(rKeys.getKeysByPattern("user:*")).thenReturn(List.of("user:1", "user:2"));
        Collection<String> keys = cache.keys("user:*");
        assertThat(keys).containsExactly("user:1", "user:2");
    }

    @Test
    void shouldGetQueue() {
        when(redissonClient.getQueue("q")).thenReturn(queue);
        assertThat(cache.getQueue("q")).isSameAs(queue);
    }

    @Test
    void shouldGetMap() {
        when(redissonClient.getMap("m")).thenReturn(rMap);
        assertThat(cache.getMap("m")).isSameAs(rMap);
    }

    @Test
    void shouldGetDefaultLock() {
        when(redissonClient.getLock("key")).thenReturn(lock);
        assertThat(cache.getLock("key", false)).isSameAs(lock);
    }

    @Test
    void shouldGetFairLock() {
        when(redissonClient.getFairLock("key")).thenReturn(lock);
        assertThat(cache.getLock("key", true)).isSameAs(lock);
    }
}
