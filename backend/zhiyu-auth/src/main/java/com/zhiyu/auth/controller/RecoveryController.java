package com.zhiyu.auth.controller;

import com.zhiyu.auth.dto.RecoveryApplyRequest;
import com.zhiyu.auth.dto.RecoveryApplyResponse;
import com.zhiyu.auth.dto.RecoveryResetRequest;
import com.zhiyu.auth.dto.RecoveryReviewRequest;
import com.zhiyu.auth.service.RecoveryService;
import com.zhiyu.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "账户恢复", description = "账户恢复工单申请与审核")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class RecoveryController {

    private final RecoveryService recoveryService;

    @Operation(summary = "提交恢复申请", description = "提交账户恢复工单，7 天内有效")
    @PostMapping("/account-recovery/apply")
    public ApiResponse<RecoveryApplyResponse> apply(@Valid @RequestBody final RecoveryApplyRequest request) {
        return ApiResponse.success(recoveryService.apply(request));
    }

    @Operation(summary = "审核恢复工单", description = "管理员审核工单，通过则生成 24 小时有效的恢复 token")
    @PostMapping("/account-recovery/{ticketNo}/review")
    public ApiResponse<Void> review(@PathVariable("ticketNo") final String ticketNo,
                                     @Valid @RequestBody final RecoveryReviewRequest request) {
        boolean approve = "APPROVED".equals(request.getDecision());
        recoveryService.review(ticketNo, approve, request.getNote(), null);
        return ApiResponse.success(null);
    }

    @Operation(summary = "通过恢复 token 重置密码", description = "使用审核通过的恢复 token 重置密码")
    @PostMapping("/account-recovery/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody final RecoveryResetRequest request) {
        recoveryService.resetPassword(request.getRecoveryToken(), request.getNewPassword());
        return ApiResponse.success(null);
    }
}
