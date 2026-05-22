package com.ufp.common.datasource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记方法或类使用的数据源。
 * 当标记在类上时，该类的所有方法使用指定数据源；方法级注解优先于类级。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UfpDS {
    /**
     * 数据源 key，对应 {@code ufp.datasource.<key>} 配置。
     * 示例：{@code "ufp_auth"}, {@code "zhiyu"}
     */
    String value();
}
