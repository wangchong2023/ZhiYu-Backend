package com.zhiyu.common.cache;

import com.zhiyu.ufp.common.cache.ICacheOperate;

import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;

/**
 * Utility for executing actions under a distributed lock obtained via
 * {@link ICacheOperate#getLock(String, boolean)}.
 */
public final class LockInstance {

    private LockInstance() {
    }

    /**
     * Execute {@code action} while holding the lock identified by {@code key}.
     *
     * @param cache  cache provider used to obtain the lock
     * @param key    lock identifier
     * @param fair   whether the lock should be fair
     * @param action the action to execute under the lock
     */
    public static void doWithLock(final ICacheOperate cache, final String key,
                                  final boolean fair, final Runnable action) {
        Lock lock = cache.getLock(key, fair);
        try {
            lock.lock();
            action.run();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Execute {@code callable} while holding the lock and return its result.
     *
     * @param callable the action to execute under the lock
     * @param cache    cache provider used to obtain the lock
     * @param key      lock identifier
     * @param fair     whether the lock should be fair
     * @param <T>      return type of the callable
     * @return the result of {@code callable.call()}
     * @throws Exception if the callable throws
     */
    public static <T> T doWithLock(final Callable<T> callable,
                                   final ICacheOperate cache, final String key,
                                   final boolean fair) throws Exception {
        Lock lock = cache.getLock(key, fair);
        try {
            lock.lock();
            return callable.call();
        } finally {
            lock.unlock();
        }
    }
}
