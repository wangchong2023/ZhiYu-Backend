package com.zhiyu.ufp.common.datasource;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Thread-local stack (Deque) holding the current datasource key.
 * Supports nested datasource switching — when a method annotated
 * with {@code @UfpDS("dbB")} is called inside a method annotated
 * with {@code @UfpDS("dbA")}, the outer datasource is restored
 * on return.
 */
public final class UfpDSContextHolder {

    private static final ThreadLocal<Deque<String>> CONTEXT = new ThreadLocal<>();

    private UfpDSContextHolder() {
    }

    private static Deque<String> deque() {
        Deque<String> deque = CONTEXT.get();
        if (deque == null) {
            deque = new ArrayDeque<>();
            CONTEXT.set(deque);
        }
        return deque;
    }

    /** Push a datasource key onto the stack. Null keys are silently ignored. */
    public static void push(final String dsKey) {
        if (dsKey != null) {
            deque().push(dsKey);
        }
    }

    /** Remove and return the top datasource key, restoring the previous one. */
    public static String poll() {
        Deque<String> dq = deque();
        String result = dq.poll();
        if (dq.isEmpty()) {
            CONTEXT.remove();
        }
        return result;
    }

    /** Return the current datasource key without removing it. */
    public static String peek() {
        return deque().peek();
    }

    /** Remove all state for the current thread. */
    public static void clear() {
        CONTEXT.remove();
    }
}
