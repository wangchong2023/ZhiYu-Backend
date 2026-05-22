package com.ufp.common.datasource;

/**
 * ThreadLocal 持有当前线程的数据源 key，供数据源路由组件读取。
 */
public final class UfpDSContextHolder {

    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    private UfpDSContextHolder() {
    }

    public static void set(String dsKey) {
        CONTEXT.set(dsKey);
    }

    public static String get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
