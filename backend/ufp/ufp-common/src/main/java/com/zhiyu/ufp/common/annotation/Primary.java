package com.zhiyu.ufp.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark a field as the primary key.
 * Used by {@link com.zhiyu.ufp.common.model.Model#getPrimary()}
 * to identify the primary key field via reflection.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Primary {
}
