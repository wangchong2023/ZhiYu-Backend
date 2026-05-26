package com.zhiyu.common.feign;

import lombok.extern.slf4j.Slf4j;

/**
 * Base class for Feign client wrappers.
 * Provides a {@code before()} hook that subclasses can override
 * for pre-call logic (validation, logging, context propagation).
 *
 * @param <T> the Feign client interface type
 */
@Slf4j
public abstract class AbstractClientOperation<T> {

    protected final T client;

    protected AbstractClientOperation(final T client) {
        this.client = client;
    }

    /**
     * Hook executed before each Feign call.
     * Default implementation is a no-op.
     * Override to add validation, logging, or context propagation.
     */
    protected void before() {
        // no-op by default
    }

    protected T client() {
        before();
        return client;
    }
}
