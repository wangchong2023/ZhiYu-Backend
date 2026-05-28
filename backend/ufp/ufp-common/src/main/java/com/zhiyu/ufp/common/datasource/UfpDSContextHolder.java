/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

package com.zhiyu.ufp.common.datasource;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 平台多数据源上下文持有器。
 *
 * <p>基于 {@link ThreadLocal} 与双端队列 {@link Deque} 维护当前线程所使用的数据源 Key 栈。
 * 完美支持嵌套式数据源切换逻辑——当在被 {@code @UfpDS("dbA")} 修饰的方法内部调用另一个
 * 被 {@code @UfpDS("dbB")} 修饰的方法时，内部方法执行完毕后，能自动弹出 dbB 并恢复外层的 dbA 路由。</p>
 *
 * @author ZhiYu Architect
 * @version 1.0.0
 * @since 2026-05-28
 */
public final class UfpDSContextHolder {

    /** 线程本地上下文：存储数据源路由 Key 栈 */
    private static final ThreadLocal<Deque<String>> CONTEXT = new ThreadLocal<>();

    /**
     * 私有构造函数，防止工具类被实例化。
     */
    private UfpDSContextHolder() {
    }

    /**
     * 内部获取或初始化当前线程的数据源栈结构。
     *
     * @return 双端队列实例，绝不为 {@code null}
     */
    private static Deque<String> deque() {
        Deque<String> deque = CONTEXT.get();
        if (deque == null) {
            deque = new ArrayDeque<>();
            CONTEXT.set(deque);
        }
        return deque;
    }

    /**
     * 将一个新的数据源路由 Key 压入栈顶。
     *
     * <p>防御性机制：若传入的 dsKey 为 {@code null}，则会被安全忽略，不做任何入栈处理。</p>
     *
     * @param dsKey 数据源唯一标识键名
     */
    public static void push(final String dsKey) {
        if (dsKey != null) {
            deque().push(dsKey);
        }
    }

    /**
     * 弹出并移除当前栈顶的数据源路由 Key，同时恢复上层嵌套的数据源环境。
     *
     * @return 弹出的栈顶数据源路由 Key；若栈已空，则返回 {@code null}
     */
    public static String poll() {
        Deque<String> dq = deque();
        String result = dq.poll();
        // 彻底清理：若栈已为空，则将整个 ThreadLocal 状态移除以防内存泄漏
        if (dq.isEmpty()) {
            CONTEXT.remove();
        }
        return result;
    }

    /**
     * 检索并获取当前正在生效的数据源路由 Key，但不进行出栈移除操作。
     *
     * @return 当前正在使用的数据源路由 Key，若栈为空则返回 {@code null}
     */
    public static String peek() {
        return deque().peek();
    }

    /**
     * 强制清空当前线程持有的所有数据源路由状态，防止线程复用场景下的数据源污染或内存泄露。
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
