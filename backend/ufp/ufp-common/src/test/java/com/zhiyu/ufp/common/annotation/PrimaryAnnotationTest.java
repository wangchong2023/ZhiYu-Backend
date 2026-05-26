package com.zhiyu.ufp.common.annotation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class PrimaryAnnotationTest {

    @Test
    void shouldBeOnField() throws Exception {
        Field field = PrimaryClass.class.getDeclaredField("id");
        Primary primary = field.getAnnotation(Primary.class);
        assertThat(primary).isNotNull();
    }

    @Test
    void shouldBeRuntimeRetention() {
        Primary primary = PrimaryClass.class.getDeclaredFields()[0].getAnnotation(Primary.class);
        assertThat(primary).isNotNull();
    }

    static class PrimaryClass {
        @Primary
        private String id;

        private String name;
    }
}
