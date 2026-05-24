package com.zhiyu.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.dto.AdminUserDto;
import com.zhiyu.admin.dto.AssignRoleRequest;
import com.zhiyu.admin.dto.CreateAdminUserRequest;
import com.zhiyu.admin.dto.ResetPasswordRequest;
import com.zhiyu.admin.dto.RoleDto;
import com.zhiyu.admin.service.AdminRbacService;
import com.zhiyu.common.web.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRbacControllerTest {

    @Mock private AdminRbacService adminRbacService;
    @InjectMocks private AdminRbacController adminRbacController;

    @Test
    void shouldListRoles() {
        RoleDto role = RoleDto.builder().roleId(1).roleName("ADMIN").roleCode("ADMIN").build();
        when(adminRbacService.listRoles()).thenReturn(List.of(role));

        ApiResponse<List<RoleDto>> resp = adminRbacController.listRoles();

        assertThat(resp.getData()).hasSize(1);
    }

    @Test
    void shouldAssignRole() {
        AssignRoleRequest req = new AssignRoleRequest();
        req.setRoleId(1);

        ApiResponse<Void> resp = adminRbacController.assignRole(1L, req);

        verify(adminRbacService).assignRole(1L, 1);
        assertThat(resp.getCode()).isZero();
    }

    @Test
    void shouldRemoveRole() {
        ApiResponse<Void> resp = adminRbacController.removeRole(1L, 1);

        verify(adminRbacService).removeRole(1L, 1);
        assertThat(resp.getCode()).isZero();
    }

    @Test
    void shouldListAdmins() {
        Page<AdminUserDto> page = new Page<>(1, 20, 0);
        when(adminRbacService.listAdminUsers(1, 20)).thenReturn(page);

        ApiResponse<Page<AdminUserDto>> resp = adminRbacController.listAdmins(1, 20);

        assertThat(resp.getData()).isNotNull();
    }

    @Test
    void shouldCreateAdmin() {
        CreateAdminUserRequest req = new CreateAdminUserRequest();
        req.setUsername("admin1");
        req.setEmail("admin1@example.com");
        req.setPassword("Admin123456");
        AdminUserDto dto = AdminUserDto.builder().userId(1L).username("admin1").build();
        when(adminRbacService.createAdminUser(any())).thenReturn(dto);

        ApiResponse<AdminUserDto> resp = adminRbacController.createAdmin(req);

        assertThat(resp.getData().getUsername()).isEqualTo("admin1");
    }

    @Test
    void shouldResetPassword() {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setNewPassword("NewPass123");

        ApiResponse<Void> resp = adminRbacController.resetPassword(1L, req);

        verify(adminRbacService).resetAdminPassword(1L, "NewPass123");
        assertThat(resp.getCode()).isZero();
    }
}
