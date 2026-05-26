package com.zhiyu.ufp.common.event;

import com.google.common.eventbus.Subscribe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventInitializingScannerTest {

    @BeforeAll
    static void setUp() {
        EventUtils.executor(Runnable::run);
    }

    @Test
    void shouldHaveCorrectBeanName() {
        assertThat(EventInitializingScanner.SCANNER_BEAN_NAME)
                .isEqualTo("EventInitializingScanner");
    }

    @Test
    void shouldSkipNullApplicationContext() {
        EventInitializingScanner scanner = new EventInitializingScanner();
        scanner.afterPropertiesSet();
    }

    @Test
    void shouldSkipSpringInternalBeans() {
        EventInitializingScanner scanner = new EventInitializingScanner();
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBeanDefinitionNames()).thenReturn(new String[]{
                "org.springframework.context.annotation.internalConfigurationAnnotationProcessor",
                "org.springframework.context.event.internalEventListenerProcessor"
        });
        scanner.setApplicationContext(ctx);
        scanner.afterPropertiesSet();
    }

    @Test
    void shouldSkipScannerBeanName() {
        EventInitializingScanner scanner = new EventInitializingScanner();
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBeanDefinitionNames()).thenReturn(new String[]{
                "EventInitializingScanner"
        });
        scanner.setApplicationContext(ctx);
        scanner.afterPropertiesSet();
    }

    @Test
    void shouldScanAndRegisterSubscribeBeans() {
        EventInitializingScanner scanner = new EventInitializingScanner();
        ApplicationContext ctx = mock(ApplicationContext.class);

        SubscribeBean subBean = new SubscribeBean();
        when(ctx.getBeanDefinitionNames()).thenReturn(new String[]{"subscribeBean"});
        when(ctx.getBean("subscribeBean")).thenReturn(subBean);

        scanner.setApplicationContext(ctx);
        scanner.afterPropertiesSet();

        EventUtils.post("test-event");
        assertThat(subBean.received).isEqualTo("test-event");

        scanner.destroy();
    }

    @Test
    void shouldUnregisterOnDestroy() {
        EventInitializingScanner scanner = new EventInitializingScanner();
        ApplicationContext ctx = mock(ApplicationContext.class);

        SubscribeBean subBean = new SubscribeBean();
        when(ctx.getBeanDefinitionNames()).thenReturn(new String[]{"subscribeBean"});
        when(ctx.getBean("subscribeBean")).thenReturn(subBean);

        scanner.setApplicationContext(ctx);
        scanner.afterPropertiesSet();
        scanner.destroy();

        subBean.received = null;
        EventUtils.post("after-destroy");
        assertThat(subBean.received).isNull();
    }

    static class SubscribeBean {
        String received;

        @Subscribe
        void onEvent(String event) {
            this.received = event;
        }
    }
}
