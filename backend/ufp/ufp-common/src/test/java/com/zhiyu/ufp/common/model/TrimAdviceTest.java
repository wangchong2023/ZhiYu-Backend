package com.zhiyu.ufp.common.model;

import com.zhiyu.ufp.common.annotation.Trim;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrimAdviceTest {

    @InjectMocks
    private TrimAdvice trimAdvice;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Test
    void shouldTrimStringArgument() throws Throwable {
        Object[] args = {"  hello world  "};
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.proceed(new Object[]{"hello world"})).thenReturn("result");

        Object result = trimAdvice.trim(joinPoint);

        assertThat(result).isEqualTo("result");
    }

    @Test
    void shouldNotModifyNonStringArguments() throws Throwable {
        Object[] args = {42, true, "  text  "};
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.proceed(new Object[]{42, true, "text"})).thenReturn("ok");

        Object result = trimAdvice.trim(joinPoint);

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void shouldHandleNullStringArgument() throws Throwable {
        Object[] args = {null, "  valid  "};
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.proceed(new Object[]{null, "valid"})).thenReturn("ok");

        Object result = trimAdvice.trim(joinPoint);

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void shouldHandleEmptyArgs() throws Throwable {
        Object[] args = {};
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.proceed(new Object[]{})).thenReturn("empty");

        Object result = trimAdvice.trim(joinPoint);

        assertThat(result).isEqualTo("empty");
    }
}
