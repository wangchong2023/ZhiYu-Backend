package com.zhiyu.ufp.common.event;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class EventBusAutoConfigurationTest {

    @Test
    void shouldCreateScannerWithProvidedExecutor() {
        EventBusAutoConfiguration config = new EventBusAutoConfiguration();

        @SuppressWarnings("unchecked")
        ObjectProvider<Executor> provider = Mockito.mock(ObjectProvider.class);
        Executor mockExecutor = Runnable::run;
        when(provider.getIfAvailable()).thenReturn(mockExecutor);

        EventInitializingScanner scanner = config.eventInitializingScanner(provider);

        assertThat(scanner).isNotNull();
    }

    @Test
    void shouldCreateScannerWithFallbackExecutor() {
        EventBusAutoConfiguration config = new EventBusAutoConfiguration();

        @SuppressWarnings("unchecked")
        ObjectProvider<Executor> provider = Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        EventInitializingScanner scanner = config.eventInitializingScanner(provider);

        assertThat(scanner).isNotNull();
    }

    @Test
    void shouldUseCorrectQualifierName() {
        // 验证限定符匹配 Spring 内置应用任务执行器 Bean 名
        assertThat(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
                .isEqualTo("applicationTaskExecutor");
    }
}
