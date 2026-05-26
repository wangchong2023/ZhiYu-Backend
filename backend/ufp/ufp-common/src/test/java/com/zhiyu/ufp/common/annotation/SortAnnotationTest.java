package com.zhiyu.ufp.common.annotation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SortAnnotationTest {

    @Test
    void shouldBeRepeatableOnFields() {
        Field[] fields = SortedClass.class.getDeclaredFields();
        Field field = Arrays.stream(fields)
                .filter(f -> f.getName().equals("name"))
                .findFirst().orElseThrow();
        com.zhiyu.ufp.common.annotation.Sort[] sorts = field.getAnnotationsByType(
                com.zhiyu.ufp.common.annotation.Sort.class);
        assertThat(sorts).hasSize(1);
        assertThat(sorts[0].index()).isZero();
        assertThat(sorts[0].direction()).isEqualTo("asc");
    }

    @Test
    void shouldHaveDefaultValues() {
        Field[] fields = SortedClass.class.getDeclaredFields();
        Field field = Arrays.stream(fields)
                .filter(f -> f.getName().equals("createdAt"))
                .findFirst().orElseThrow();
        com.zhiyu.ufp.common.annotation.Sort sort = field.getAnnotation(
                com.zhiyu.ufp.common.annotation.Sort.class);
        assertThat(sort.index()).isZero();
        assertThat(sort.property()).isEmpty();
        assertThat(sort.direction()).isEqualTo("asc");
    }

    @Test
    void shouldAllowCustomDirection() {
        Field[] fields = SortedClass.class.getDeclaredFields();
        Field field = Arrays.stream(fields)
                .filter(f -> f.getName().equals("customDir"))
                .findFirst().orElseThrow();
        com.zhiyu.ufp.common.annotation.Sort sort = field.getAnnotation(
                com.zhiyu.ufp.common.annotation.Sort.class);
        assertThat(sort.direction()).isEqualTo("desc");
        assertThat(sort.index()).isEqualTo(1);
    }

    static class SortedClass {
        @com.zhiyu.ufp.common.annotation.Sort(index = 0, direction = "asc")
        private String name;

        @com.zhiyu.ufp.common.annotation.Sort
        private String createdAt;

        @com.zhiyu.ufp.common.annotation.Sort(index = 1, direction = "desc")
        private String customDir;
    }
}
