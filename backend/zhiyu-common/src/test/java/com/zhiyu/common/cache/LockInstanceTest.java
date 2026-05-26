package com.zhiyu.common.cache;

import com.zhiyu.ufp.common.cache.ICacheOperate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LockInstanceTest {

    @Mock
    private ICacheOperate cache;

    @Mock
    private Lock lock;

    @Test
    void shouldExecuteRunnableUnderLock() {
        when(cache.getLock("test-lock", false)).thenReturn(lock);
        AtomicBoolean executed = new AtomicBoolean(false);
        LockInstance.doWithLock(cache, "test-lock", false, () -> executed.set(true));
        assertThat(executed).isTrue();
        verify(lock).lock();
        verify(lock).unlock();
    }

    @Test
    void shouldExecuteCallableUnderLock() throws Exception {
        when(cache.getLock("callable-lock", false)).thenReturn(lock);
        Integer result = LockInstance.doWithLock(() -> 42, cache, "callable-lock", false);
        assertThat(result).isEqualTo(42);
        verify(lock).lock();
        verify(lock).unlock();
    }

    @Test
    void shouldUnlockEvenOnException() {
        when(cache.getLock("safe-lock", false)).thenReturn(lock);
        assertThatThrownBy(() ->
                LockInstance.doWithLock(cache, "safe-lock", false, () -> {
                    throw new RuntimeException("fail");
                })
        ).isInstanceOf(RuntimeException.class);
        verify(lock).unlock();
    }
}
