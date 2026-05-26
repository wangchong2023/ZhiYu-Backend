package com.zhiyu.ufp.common.event;

import com.google.common.eventbus.EventBus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Spring Boot auto-configuration for the in-process EventBus.
 *
 * <p>Activated for Servlet-based web applications when Guava's
 * {@link EventBus} is on the classpath.  Sets the async executor
 * from Spring's application task executor (falling back to an
 * 8-thread fixed pool) and creates the {@link EventInitializingScanner}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(EventBus.class)
public class EventBusAutoConfiguration {

    private static final int FALLBACK_THREAD_POOL_SIZE = 8;

    @Bean(name = EventInitializingScanner.SCANNER_BEAN_NAME)
    EventInitializingScanner eventInitializingScanner(
            @org.springframework.beans.factory.annotation.Qualifier(
                    TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
            final ObjectProvider<Executor> executors) {
        EventUtils.executor(executors.getIfAvailable(
                () -> Executors.newFixedThreadPool(FALLBACK_THREAD_POOL_SIZE)));
        return new EventInitializingScanner();
    }
}
