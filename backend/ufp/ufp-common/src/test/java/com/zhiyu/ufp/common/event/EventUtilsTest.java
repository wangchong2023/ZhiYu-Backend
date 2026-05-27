package com.zhiyu.ufp.common.event;

import com.google.common.eventbus.Subscribe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class EventUtilsTest {

    @BeforeAll
    static void setUp() {
        EventUtils.executor(Runnable::run);
    }

    @Test
    void shouldPostAndReceiveSynchronously() {
        Subscriber sub = new Subscriber();
        EventUtils.register(sub);
        EventUtils.post("hello");
        assertThat(sub.received).isEqualTo("hello");
    }

    @Test
    void shouldPostAndReceiveAsync() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AsyncSubscriber sub = new AsyncSubscriber(latch);
        EventUtils.register(sub);
        EventUtils.asyncPost("async-hello");
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(sub.received).isEqualTo("async-hello");
    }

    @Test
    void shouldRegisterWithBothBuses() {
        Subscriber sub = new Subscriber();
        assertThatCode(() -> EventUtils.register(sub)).doesNotThrowAnyException();
    }

    @Test
    void shouldUnregisterFromBothBuses() {
        Subscriber sub = new Subscriber();
        EventUtils.register(sub);
        assertThatCode(() -> EventUtils.unregister(sub)).doesNotThrowAnyException();
        // 注销后发布的事件不应再被订阅者接收
        EventUtils.post("should-not-arrive");
        assertThat(sub.received).isNull();
    }

    @Test
    void shouldSetExecutorBeforeAsyncPost() {
        EventUtils.executor(Runnable::run);
        assertThatCode(() -> EventUtils.asyncPost("test")).doesNotThrowAnyException();
    }

    static class Subscriber {
        String received;

        @Subscribe
        void onEvent(String event) {
            this.received = event;
        }
    }

    static class AsyncSubscriber {
        String received;
        final CountDownLatch latch;

        AsyncSubscriber(CountDownLatch latch) {
            this.latch = latch;
        }

        @Subscribe
        void onEvent(String event) {
            this.received = event;
            latch.countDown();
        }
    }
}
