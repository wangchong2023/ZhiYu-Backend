package com.zhiyu.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MyBatisPlusConfigTest {

    private final MyBatisPlusConfig config = new MyBatisPlusConfig();

    @Test
    void shouldCreateMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = config.mybatisPlusInterceptor();

        assertThat(interceptor).isNotNull();
        assertThat(interceptor.getInterceptors()).hasSize(1);
        assertThat(interceptor.getInterceptors().get(0))
                .isInstanceOf(PaginationInnerInterceptor.class);
    }

    @Test
    void shouldConfigurePaginationForMySQL() {
        MybatisPlusInterceptor interceptor = config.mybatisPlusInterceptor();

        PaginationInnerInterceptor pagination =
                (PaginationInnerInterceptor) interceptor.getInterceptors().get(0);
        assertThat(pagination.getDbType()).isEqualTo(DbType.MYSQL);
    }
}
