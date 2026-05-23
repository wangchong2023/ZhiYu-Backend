package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@Schema(description = "用户详情")
public class AdminUserDetailDto {
    @Schema(description = "用户ID") private Long userId;
    @Schema(description = "用户名") private String username;
    @Schema(description = "邮箱") private String email;
    @Schema(description = "手机") private String mobile;
    @Schema(description = "注册时间") private LocalDateTime createdAt;
    @Schema(description = "状态") private String status;
    @Schema(description = "范围") private String scope;
    @Schema(description = "最近10条登录记录") private List<LoginLogDto> recentLogs;
}
