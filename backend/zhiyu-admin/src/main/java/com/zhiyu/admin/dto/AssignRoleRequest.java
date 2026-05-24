package com.zhiyu.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignRoleRequest {
    @NotNull
    private Integer roleId;
}
