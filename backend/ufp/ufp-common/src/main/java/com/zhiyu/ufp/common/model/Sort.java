package com.zhiyu.ufp.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Sort parameter model.
 * Represents a single sort directive with property name and direction.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Sort implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Sort priority (lower = higher priority). */
    private Integer index;

    /** The property/field name to sort by. */
    private String property;

    /** Sort direction: "asc" or "desc". */
    private String direction;
}
