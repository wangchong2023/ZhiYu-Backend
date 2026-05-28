/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

package com.zhiyu.subscription.controller;

import com.zhiyu.common.web.ApiResponse;
import com.zhiyu.subscription.dto.PlanDto;
import com.zhiyu.subscription.service.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 智宇平台订阅套餐控制层。
 *
 * <p>提供面向前台客户端与后端的公共套餐产品目录接口，包括已上架套餐列表的高效查询、
 * 以及指定套餐配置项详情的主键检索。</p>
 *
 * @author ZhiYu Architect
 * @version 1.0.0
 * @since 2026-05-28
 */
@Tag(name = "套餐", description = "套餐查询接口")
@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    /**
     * 获取所有生效中的套餐列表。
     *
     * <p>查询系统当前所有已激活并上架的订阅套餐，结果按后台设定的排序权重字段升序返回。</p>
     *
     * @return 统一 API 响应体，内部包含已上架套餐列表 DTO
     */
    @Operation(summary = "获取套餐列表", description = "查询所有已启用的套餐，按排序字段升序")
    @GetMapping
    public ApiResponse<List<PlanDto>> listActivePlans() {
        return ApiResponse.success(planService.listActivePlans());
    }

    /**
     * 获取指定套餐的配置详情。
     *
     * @param id 套餐记录的主键 ID
     * @return 统一 API 响应体，包含对应的套餐详情 DTO
     */
    @Operation(summary = "获取套餐详情", description = "根据套餐ID查询套餐信息")
    @GetMapping("/{id}")
    public ApiResponse<PlanDto> getPlan(@PathVariable("id") final Long id) {
        return ApiResponse.success(planService.getPlan(id));
    }
}
