package com.zhiyu.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisConfigTest {

    private final RedisConfig config = new RedisConfig();

    @Test
    void shouldCreateRedisTemplate() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);

        RedisTemplate<String, Object> template = config.redisTemplate(factory);

        assertThat(template).isNotNull();
        assertThat(template.getConnectionFactory()).isSameAs(factory);
    }

    @Test
    void shouldUseStringKeySerializer() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);

        RedisTemplate<String, Object> template = config.redisTemplate(factory);

        assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getHashKeySerializer()).isInstanceOf(StringRedisSerializer.class);
    }

    @Test
    void shouldUseJsonValueSerializer() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);

        RedisTemplate<String, Object> template = config.redisTemplate(factory);

        assertThat(template.getValueSerializer()).isInstanceOf(Jackson2JsonRedisSerializer.class);
        assertThat(template.getHashValueSerializer()).isInstanceOf(Jackson2JsonRedisSerializer.class);
    }
}
