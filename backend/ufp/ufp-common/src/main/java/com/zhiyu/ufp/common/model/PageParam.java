package com.zhiyu.ufp.common.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Pagination parameter model.
 * Carries page number, page size, and whether to fetch the total count.
 */
@Data
public class PageParam implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_PAGE_SIZE = 20;

    /** Current page number, starting from 1. */
    private int pageNo = 1;

    /** Number of records per page. */
    private int pageSize = DEFAULT_PAGE_SIZE;

    /** Whether to execute a count query for total records. */
    private boolean fetchCount = true;
}
