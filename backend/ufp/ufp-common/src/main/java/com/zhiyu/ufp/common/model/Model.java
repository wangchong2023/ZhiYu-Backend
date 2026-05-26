package com.zhiyu.ufp.common.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zhiyu.ufp.common.annotation.Primary;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Abstract base class for entities and DTOs.
 * Provides pagination/sort parameters and primary key resolution via reflection.
 */
@Data
public abstract class Model implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(hidden = true)
    private PageParam page;

    @Schema(hidden = true)
    private List<Sort> sorts;

    /**
     * Get the primary key value of the entity.
     * Uses reflection to find the field annotated with {@link Primary}.
     *
     * @return the primary key value as a String, or null if not found
     */
    @JsonIgnore
    public String getPrimary() {
        return getPrimaryValue(this.getClass());
    }

    @SuppressWarnings({"PMD.AvoidAccessibilityAlteration", "PMD.EmptyCatchBlock"})
    private String getPrimaryValue(final Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            Primary primary = field.getAnnotation(Primary.class);
            if (primary != null) {
                field.setAccessible(true);
                try {
                    return (String) field.get(this);
                } catch (IllegalAccessException e) {
                    // field is inaccessible despite setAccessible — skip
                }
            }
        }
        Class<?> superclass = clazz.getSuperclass();
        if (superclass == null || superclass.equals(Object.class)) {
            return null;
        }
        return getPrimaryValue(superclass);
    }

    /**
     * Get primary key values split by comma.
     * Useful for composite keys stored as "id1,id2".
     *
     * @return list of primary key segments, or null if no primary key
     */
    @JsonIgnore
    public List<String> getPrimaries() {
        String primary = getPrimary();
        if (primary == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(Arrays.asList(primary.split(",")));
    }
}
