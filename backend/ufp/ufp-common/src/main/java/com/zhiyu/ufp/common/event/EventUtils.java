package com.zhiyu.ufp.common.event;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;

import java.util.concurrent.Executor;

/**
 * Static utility wrapping Guava EventBus for synchronous and asynchronous
 * in-process event publishing.
 *
 * <p>Thread-safe via double-checked locking on both the synchronous and
 * asynchronous bus singletons.  Callers never interact with the buses
 * directly — use the static helper methods.
 */
public final class EventUtils {

    @SuppressWarnings("PMD.AvoidUsingVolatile")
    private static volatile EventBus eventBus;
    @SuppressWarnings("PMD.AvoidUsingVolatile")
    private static volatile AsyncEventBus asyncEventBus;
    @SuppressWarnings("PMD.AvoidUsingVolatile")
    private static volatile Executor executor;

    private EventUtils() {
        // utility class — no instances
    }

    // ── executor configuration ────────────────────────────────

    /**
     * Set the executor used by the {@link AsyncEventBus}.
     * Must be called <strong>before</strong> the first call to
     * {@link #asyncPost(Object)}, otherwise the default executor
     * (direct-executor on the posting thread) is locked in.
     */
    public static void executor(final Executor ex) {
        executor = ex;
    }

    // ── singleton accessors (double-checked locking) ──────────

    private static EventBus getEventBus() {
        if (eventBus == null) {
            synchronized (EventUtils.class) {
                if (eventBus == null) {
                    eventBus = new EventBus();
                }
            }
        }
        return eventBus;
    }

    private static AsyncEventBus getAsyncEventBus() {
        if (asyncEventBus == null) {
            synchronized (EventUtils.class) {
                if (asyncEventBus == null) {
                    asyncEventBus = new AsyncEventBus(executor);
                }
            }
        }
        return asyncEventBus;
    }

    // ── publish ───────────────────────────────────────────────

    /** Publish an event synchronously on the calling thread. */
    public static void post(final Object event) {
        getEventBus().post(event);
    }

    /** Publish an event asynchronously via the configured executor. */
    public static void asyncPost(final Object event) {
        getAsyncEventBus().post(event);
    }

    // ── subscriber management ─────────────────────────────────

    /**
     * Register a subscriber with both the synchronous and asynchronous
     * event buses.  Any {@code @Subscribe}-annotated methods on the
     * object will be discovered.
     */
    public static void register(final Object object) {
        getEventBus().register(object);
        getAsyncEventBus().register(object);
    }

    /** Unregister a previously-registered subscriber from both buses. */
    public static void unregister(final Object object) {
        getEventBus().unregister(object);
        getAsyncEventBus().unregister(object);
    }
}
