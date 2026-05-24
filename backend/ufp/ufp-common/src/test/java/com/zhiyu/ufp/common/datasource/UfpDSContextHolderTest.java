package com.zhiyu.ufp.common.datasource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UfpDSContextHolderTest {

    @AfterEach
    void tearDown() {
        UfpDSContextHolder.clear();
    }

    @Test
    void shouldSetAndGetDataSourceKey() {
        UfpDSContextHolder.set("ufp_auth");

        assertThat(UfpDSContextHolder.get()).isEqualTo("ufp_auth");
    }

    @Test
    void shouldReturnNullWhenNotSet() {
        assertThat(UfpDSContextHolder.get()).isNull();
    }

    @Test
    void shouldClearContext() {
        UfpDSContextHolder.set("zhiyu_db");
        assertThat(UfpDSContextHolder.get()).isEqualTo("zhiyu_db");

        UfpDSContextHolder.clear();
        assertThat(UfpDSContextHolder.get()).isNull();
    }

    @Test
    void shouldOverwritePreviousValue() {
        UfpDSContextHolder.set("first");
        UfpDSContextHolder.set("second");

        assertThat(UfpDSContextHolder.get()).isEqualTo("second");
    }

    @Test
    void shouldHandleNullOrEmptyKey() {
        UfpDSContextHolder.set(null);

        assertThat(UfpDSContextHolder.get()).isNull();
    }
}
