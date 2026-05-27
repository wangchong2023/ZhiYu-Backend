package com.zhiyu.ufp.common.event;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;

import java.util.concurrent.Executor;

/**
 * 基于 Guava EventBus 封装的进程内同步/异步事件发布静态工具类。
 *
 * <p>通过双重检查锁（Double-Checked Locking）模式对同步及异步 EventBus 实例进行延迟加载与实例化。
 * 调用方统一通过本工具的静态方法进行事件注册、反注册及投递。</p>
 *
 * @author ZhiYu
 * @since 1.0.0
 */
public final class EventUtils {

    /**
     * 同步事件总线实例。
     *
     * <p>由于并发环境下双重锁检测机制的需要，防止指令重排引起并发可见性安全隐患，
     * 必须在此使用 {@code volatile} 关键字修饰。
     * PMD 静态分析工具默认不推荐使用 volatile 属性，
     * 故使用 {@code @SuppressWarnings("PMD.AvoidUsingVolatile")} 抑制该规则警告。</p>
     */
    @SuppressWarnings("PMD.AvoidUsingVolatile")
    private static volatile EventBus eventBus;

    /**
     * 异步事件总线实例。
     *
     * <p>同上，必须使用 {@code volatile} 关键字。使用 {@code @SuppressWarnings("PMD.AvoidUsingVolatile")} 抑制警告。</p>
     */
    @SuppressWarnings("PMD.AvoidUsingVolatile")
    private static volatile AsyncEventBus asyncEventBus;

    /**
     * 异步事件总线绑定的线程池执行器。
     *
     * <p>同上，必须使用 {@code volatile} 关键字。使用 {@code @SuppressWarnings("PMD.AvoidUsingVolatile")} 抑制警告。</p>
     */
    @SuppressWarnings("PMD.AvoidUsingVolatile")
    private static volatile Executor executor;

    private EventUtils() {
        // 静态工具类，防止实例化
    }

    // ── 线程池配置 ────────────────────────────────

    /**
     * 配置异步事件总线使用的线程池。
     *
     * <p>必须在首次触发 {@link #asyncPost(Object)} 异步投递前进行配置，
     * 否则将默认绑定到调用线程的直接执行器（DirectExecutor）。</p>
     *
     * @param ex 线程池执行器
     */
    public static void executor(final Executor ex) {
        executor = ex;
    }

    // ── 单例获取方法（双重检查锁） ──────────────────────────

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

    // ── 事件投递 ───────────────────────────────────────────────

    /**
     * 同步投递事件（阻塞式，在调用者当前线程中执行）。
     *
     * @param event 事件对象
     */
    public static void post(final Object event) {
        getEventBus().post(event);
    }

    /**
     * 异步投递事件（非阻塞，在绑定的执行器线程池中运行）。
     *
     * @param event 事件对象
     */
    public static void asyncPost(final Object event) {
        getAsyncEventBus().post(event);
    }

    // ── 订阅管理 ─────────────────────────────────

    /**
     * 注册事件订阅者。
     *
     * <p>自动扫描订阅者类中所有带有 {@code @Subscribe} 注解的方法，
     * 并分别向同步和异步事件总线进行注册绑定。</p>
     *
     * @param object 订阅者实例
     */
    public static void register(final Object object) {
        getEventBus().register(object);
        getAsyncEventBus().register(object);
    }

    /**
     * 注销并取消事件订阅者绑定。
     *
     * @param object 订阅者实例
     */
    public static void unregister(final Object object) {
        getEventBus().unregister(object);
        getAsyncEventBus().unregister(object);
    }
}
