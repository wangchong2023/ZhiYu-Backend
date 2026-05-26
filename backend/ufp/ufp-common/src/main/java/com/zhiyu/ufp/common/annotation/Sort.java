package com.zhiyu.ufp.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative sort field marker.
 * Apply to fields to specify default sort order for queries.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Sorts.class)
public @interface Sort {

    /** Sort priority (lower = higher priority). */
    int index() default 0;

    /** Override the property name for sorting (defaults to field name). */
    String property() default "";

    /** Sort direction: "asc" or "desc". */
    String direction() default "asc";
}
