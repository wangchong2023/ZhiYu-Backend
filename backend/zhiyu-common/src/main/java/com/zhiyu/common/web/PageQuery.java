package com.zhiyu.common.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Shared pagination query parameters for controller endpoints.
 * Usage: {@code ApiResponse<Page<T>> list(@Valid PageQuery pageQuery, ...)}
 * Replaces the repeated @RequestParam(defaultValue = "1") int page,
 * @RequestParam(defaultValue = "20") int size pattern.
 */
@Data
public class PageQuery {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    @Min(1)
    private int page = DEFAULT_PAGE;

    @Min(1)
    @Max(MAX_PAGE_SIZE)
    private int size = DEFAULT_SIZE;
}
