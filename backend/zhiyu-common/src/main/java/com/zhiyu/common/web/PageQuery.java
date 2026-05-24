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

    @Min(1)
    private int page = 1;

    @Min(1)
    @Max(100)
    private int size = 20;
}
