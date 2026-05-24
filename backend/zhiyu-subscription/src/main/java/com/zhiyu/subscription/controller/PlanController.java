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

@Tag(name = "套餐", description = "套餐查询接口")
@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @Operation(summary = "获取套餐列表", description = "查询所有已启用的套餐，按排序字段升序")
    @GetMapping
    public ApiResponse<List<PlanDto>> listActivePlans() {
        return ApiResponse.success(planService.listActivePlans());
    }

    @Operation(summary = "获取套餐详情", description = "根据套餐ID查询套餐信息")
    @GetMapping("/{id}")
    public ApiResponse<PlanDto> getPlan(@PathVariable("id") final Long id) {
        return ApiResponse.success(planService.getPlan(id));
    }
}
