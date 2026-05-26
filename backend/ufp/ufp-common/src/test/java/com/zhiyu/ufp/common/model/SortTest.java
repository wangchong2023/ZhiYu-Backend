package com.zhiyu.ufp.common.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SortTest {

    @Test
    void shouldCreateWithNoArgsConstructor() {
        Sort sort = new Sort();
        sort.setIndex(1);
        sort.setProperty("username");
        sort.setDirection("asc");
        assertThat(sort.getIndex()).isEqualTo(1);
        assertThat(sort.getProperty()).isEqualTo("username");
        assertThat(sort.getDirection()).isEqualTo("asc");
    }

    @Test
    void shouldCreateWithAllArgsConstructor() {
        Sort sort = new Sort(1, "createdAt", "desc");
        assertThat(sort.getIndex()).isEqualTo(1);
        assertThat(sort.getProperty()).isEqualTo("createdAt");
        assertThat(sort.getDirection()).isEqualTo("desc");
    }

    @Test
    void shouldSupportDescDirection() {
        Sort sort = new Sort(0, "id", "desc");
        assertThat(sort.getDirection()).isEqualTo("desc");
        assertThat(sort.getIndex()).isZero();
    }
}
