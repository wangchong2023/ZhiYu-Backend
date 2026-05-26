package com.zhiyu.ufp.common.model;

import com.zhiyu.ufp.common.annotation.Primary;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelTest {

    @Test
    void shouldGetPrimaryFromAnnotatedField() {
        TestEntity entity = new TestEntity();
        entity.setId("12345");
        assertThat(entity.getPrimary()).isEqualTo("12345");
    }

    @Test
    void shouldReturnNullWhenNoPrimaryAnnotated() {
        NoPrimaryEntity entity = new NoPrimaryEntity();
        entity.setName("test");
        assertThat(entity.getPrimary()).isNull();
    }

    @Test
    void shouldGetPrimariesAsList() {
        TestEntity entity = new TestEntity();
        entity.setId("a,b,c");
        assertThat(entity.getPrimaries()).containsExactly("a", "b", "c");
    }

    @Test
    void shouldReturnEmptyPrimariesWhenNoPrimary() {
        NoPrimaryEntity entity = new NoPrimaryEntity();
        assertThat(entity.getPrimaries()).isEmpty();
    }

    @Test
    void shouldGetPrimaryFromSuperclass() {
        ChildEntity entity = new ChildEntity();
        entity.setId("inherited-id");
        entity.setExtra("extra-data");
        assertThat(entity.getPrimary()).isEqualTo("inherited-id");
    }

    @Test
    void shouldHaveDefaultPageAndSorts() {
        TestEntity entity = new TestEntity();
        assertThat(entity.getPage()).isNull();
        assertThat(entity.getSorts()).isNull();
    }

    static class TestEntity extends Model {
        @Primary
        private String id;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    static class NoPrimaryEntity extends Model {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    static class ChildEntity extends TestEntity {
        private String extra;

        public String getExtra() {
            return extra;
        }

        public void setExtra(String extra) {
            this.extra = extra;
        }
    }
}
