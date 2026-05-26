package com.zhiyu.ufp.common.aop;

import com.zhiyu.ufp.common.datasource.UfpDS;
import com.zhiyu.ufp.common.datasource.UfpDSContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UfpDSAspectTest {

    @InjectMocks
    private UfpDSAspect aspect;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @AfterEach
    void tearDown() {
        UfpDSContextHolder.clear();
    }

    @Test
    void shouldPushAndPollDatasourceAroundClass() throws Throwable {
        UfpDS ufpDS = ClassLevelUfpDS.class.getAnnotation(UfpDS.class);
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.aroundClass(joinPoint, ufpDS);

        assertThat(result).isEqualTo("result");
        assertThat(UfpDSContextHolder.peek()).isNull();
    }

    @Test
    void shouldPushAndPollDatasourceAroundMethod() throws Throwable {
        UfpDS ufpDS = getMethodAnnotation();
        when(joinPoint.proceed()).thenReturn(42);

        Object result = aspect.aroundMethod(joinPoint, ufpDS);

        assertThat(result).isEqualTo(42);
        assertThat(UfpDSContextHolder.peek()).isNull();
    }

    @Test
    void shouldRestoreDatasourceOnException() throws Throwable {
        UfpDS ufpDS = ClassLevelUfpDS.class.getAnnotation(UfpDS.class);
        when(joinPoint.proceed()).thenThrow(new RuntimeException("test error"));

        try {
            aspect.aroundClass(joinPoint, ufpDS);
        } catch (RuntimeException ignored) {
        }

        assertThat(UfpDSContextHolder.peek()).isNull();
    }

    @Test
    void shouldSupportNestedDatasource() throws Throwable {
        UfpDS ufpDS = ClassLevelUfpDS.class.getAnnotation(UfpDS.class);
        when(joinPoint.proceed()).thenReturn("ok");

        UfpDSContextHolder.push("outer");
        Object result = aspect.aroundClass(joinPoint, ufpDS);

        assertThat(result).isEqualTo("ok");
        assertThat(UfpDSContextHolder.peek()).isEqualTo("outer");

        UfpDSContextHolder.poll();
    }

    @UfpDS("test_db")
    static class ClassLevelUfpDS {
    }

    @UfpDS("test_db")
    public void methodWithUfpDS() {
    }

    private UfpDS getMethodAnnotation() {
        try {
            return UfpDSAspectTest.class.getMethod("methodWithUfpDS").getAnnotation(UfpDS.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
