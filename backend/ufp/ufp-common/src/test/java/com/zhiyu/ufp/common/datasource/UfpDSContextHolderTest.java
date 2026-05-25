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
    void shouldPushAndPeekDataSourceKey() {
        UfpDSContextHolder.push("ufp_auth");
        assertThat(UfpDSContextHolder.peek()).isEqualTo("ufp_auth");
    }

    @Test
    void shouldReturnNullWhenNotSet() {
        assertThat(UfpDSContextHolder.peek()).isNull();
    }

    @Test
    void shouldClearContext() {
        UfpDSContextHolder.push("zhiyu_db");
        assertThat(UfpDSContextHolder.peek()).isEqualTo("zhiyu_db");

        UfpDSContextHolder.clear();
        assertThat(UfpDSContextHolder.peek()).isNull();
    }

    @Test
    void shouldSupportNestedSwitching() {
        UfpDSContextHolder.push("first");
        UfpDSContextHolder.push("second");

        assertThat(UfpDSContextHolder.peek()).isEqualTo("second");
        assertThat(UfpDSContextHolder.poll()).isEqualTo("second");
        assertThat(UfpDSContextHolder.peek()).isEqualTo("first");
        assertThat(UfpDSContextHolder.poll()).isEqualTo("first");
        assertThat(UfpDSContextHolder.peek()).isNull();
    }

    @Test
    void shouldHandleNullKey() {
        UfpDSContextHolder.push(null);
        assertThat(UfpDSContextHolder.peek()).isNull();
    }
}
