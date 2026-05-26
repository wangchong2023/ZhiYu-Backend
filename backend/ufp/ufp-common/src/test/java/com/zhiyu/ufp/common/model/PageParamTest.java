package com.zhiyu.ufp.common.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageParamTest {

    @Test
    void shouldHaveDefaultValues() {
        PageParam page = new PageParam();
        assertThat(page.getPageNo()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(20);
        assertThat(page.isFetchCount()).isTrue();
    }

    @Test
    void shouldAllowCustomValues() {
        PageParam page = new PageParam();
        page.setPageNo(3);
        page.setPageSize(50);
        page.setFetchCount(false);
        assertThat(page.getPageNo()).isEqualTo(3);
        assertThat(page.getPageSize()).isEqualTo(50);
        assertThat(page.isFetchCount()).isFalse();
    }
}
