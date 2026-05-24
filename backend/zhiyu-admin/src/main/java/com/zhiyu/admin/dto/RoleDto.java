package com.zhiyu.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleDto {
    private Integer roleId;
    private String roleName;
    private String roleCode;
    private Integer enabled;
    private String description;
}
