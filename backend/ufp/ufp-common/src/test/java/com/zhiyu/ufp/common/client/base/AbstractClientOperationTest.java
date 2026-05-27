/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

package com.zhiyu.ufp.common.client.base;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 远程调用执行基类测试类。
 *
 * <p>测试验证通用 RPC 包装模板方法在执行正常逻辑及发生故障抛出异常时的 before/after 生命周期执行契合性。</p>
 *
 * @author ZhiYu
 * @since 1.0.0
 */
class AbstractClientOperationTest {

    /**
     * 测试当 RPC 执行成功时，执行基类是否能依次回调 before()、执行业务以及回调 after()。
     */
    @Test
    void shouldInvokeBeforeAndAfterHooksDuringExecution() {
        final AtomicBoolean beforeInvoked = new AtomicBoolean(false);
        final AtomicBoolean afterInvoked = new AtomicBoolean(false);

        final AbstractClientOperation<String> operation = new AbstractClientOperation<>() {
            @Override
            protected void before() {
                beforeInvoked.set(true);
            }

            @Override
            protected void after(final String result) {
                afterInvoked.set(true);
                assertThat(result).isEqualTo("success-result");
            }
        };

        final String executeResult = operation.execute(() -> "success-result");

        assertThat(executeResult).isEqualTo("success-result");
        assertThat(beforeInvoked.get()).isTrue();
        assertThat(afterInvoked.get()).isTrue();
    }

    /**
     * 测试当服务调用抛出异常时，包装基类能够打印异常并原样抛出，不截断异常栈。
     */
    @Test
    void shouldPropagateExceptionWhenExecutionFails() {
        final AbstractClientOperation<String> operation = new AbstractClientOperation<>() {};

        assertThatThrownBy(() -> operation.execute(() -> {
            throw new RuntimeException("RPC Timeout");
        })).isInstanceOf(RuntimeException.class).hasMessage("RPC Timeout");
    }
}
