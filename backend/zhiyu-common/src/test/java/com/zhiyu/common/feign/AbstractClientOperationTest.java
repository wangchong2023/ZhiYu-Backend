package com.zhiyu.common.feign;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AbstractClientOperationTest {

    @Test
    void shouldStoreClient() {
        TestClient mockClient = mock(TestClient.class);
        TestOperation op = new TestOperation(mockClient);
        assertThat(op.getStoredClient()).isSameAs(mockClient);
    }

    @Test
    void shouldInvokeBeforeOnClientAccess() {
        AtomicBoolean beforeCalled = new AtomicBoolean(false);
        TestClient mockClient = mock(TestClient.class);
        TestOperationWithHook op = new TestOperationWithHook(mockClient, beforeCalled);

        TestClient result = op.client();

        assertThat(result).isSameAs(mockClient);
        assertThat(beforeCalled).isTrue();
    }

    @Test
    void shouldDefaultBeforeNoOp() {
        TestClient mockClient = mock(TestClient.class);
        TestOperation op = new TestOperation(mockClient);

        TestClient result = op.client();

        assertThat(result).isSameAs(mockClient);
    }

    interface TestClient {
        String call();
    }

    static class TestOperation extends AbstractClientOperation<TestClient> {
        TestOperation(TestClient client) {
            super(client);
        }

        TestClient getStoredClient() {
            return client;
        }
    }

    static class TestOperationWithHook extends AbstractClientOperation<TestClient> {
        private final AtomicBoolean beforeCalled;

        TestOperationWithHook(TestClient client, AtomicBoolean beforeCalled) {
            super(client);
            this.beforeCalled = beforeCalled;
        }

        @Override
        protected void before() {
            beforeCalled.set(true);
        }
    }
}
