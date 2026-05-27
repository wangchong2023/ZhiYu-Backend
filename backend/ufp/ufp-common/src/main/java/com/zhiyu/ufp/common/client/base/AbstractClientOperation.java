/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

package com.zhiyu.ufp.common.client.base;

import com.zhiyu.ufp.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import java.util.function.Supplier;

/**
 * 服务间客户端远程调用执行基类。
 *
 * <p>采用模板方法设计模式，封装了远程 Feign 客户端的执行调用链。
 * 提供 {@code before()} 参数及权限校验钩子、{@code after()} 成功后置处理钩子、
 * 并在异常抛出时提供精细化的重试/防卫逻辑，以统一所有二方服务调用的入参审计与性能耗时统计。</p>
 *
 * @param <T> 服务调用返回值的类型
 * @author ZhiYu
 * @since 1.0.0
 */
@Slf4j
public abstract class AbstractClientOperation<T> {

    /**
     * 自动在调用前执行的校验钩子方法。
     * 子类可选择覆盖该方法以实现 RPC 调用前置防御和入参验证。
     */
    protected void before() {
        // 默认空实现
    }

    /**
     * 自动在调用成功后执行的后置钩子方法。
     *
     * @param result 调用返回结果
     */
    protected void after(final T result) {
        // 默认空实现
    }

    /**
     * 模板执行方法，包装实际的远程接口调用。
     *
     * @param operation 远程 RPC 调用的 Lambda/Supplier 表达式
     * @return 接口调用的返回值
     * @throws BizException 业务解码抛出的本地异常
     */
    public T execute(final Supplier<T> operation) {
        before();
        final long start = System.currentTimeMillis();
        T result;
        try {
            result = operation.get();
            after(result);
            return result;
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error("[CLIENT-RPC-ERROR] 服务间调用发生异常，耗时: {}ms, 异常消息: {}", 
                        (System.currentTimeMillis() - start), e.getMessage());
            }
            throw e;
        } finally {
            if (log.isDebugEnabled()) {
                log.debug("[CLIENT-RPC] 远程调用耗时: {}ms", (System.currentTimeMillis() - start));
            }
        }
    }
}
