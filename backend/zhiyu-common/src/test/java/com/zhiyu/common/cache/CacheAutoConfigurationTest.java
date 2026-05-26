package com.zhiyu.common.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheAutoConfigurationTest {

    @Test
    void shouldCreateLocalCacheOperate() {
        CacheAutoConfiguration config = new CacheAutoConfiguration();
        LocalCacheOperate cache = config.localCacheOperate();
        assertThat(cache).isNotNull();
        assertThat(cache.name()).isEqualTo("local");
    }
}
