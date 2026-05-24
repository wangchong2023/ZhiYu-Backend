package com.zhiyu.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.dto.AdminUserDto;
import com.zhiyu.admin.dto.CreateAdminUserRequest;
import com.zhiyu.admin.dto.RoleDto;
import com.zhiyu.ufp.auth.entity.AuthRole;
import com.zhiyu.ufp.auth.entity.AuthRoleUserRelation;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.mapper.AuthRoleMapper;
import com.zhiyu.ufp.auth.mapper.AuthRoleUserRelationMapper;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.auth.password.PasswordService;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRbacServiceTest {

    @Mock private AuthRoleMapper authRoleMapper;
    @Mock private AuthRoleUserRelationMapper roleUserRelationMapper;
    @Mock private AuthUserMapper authUserMapper;
    @Mock private PasswordService passwordService;
    @InjectMocks private AdminRbacService adminRbacService;

    @Test
    void shouldListEnabledRoles() {
        AuthRole role = AuthRole.builder()
                .authRoleId(1).authRoleName("ADMIN").authRoleCode("ADMIN")
                .authRoleEnable(1).authRoleDesc("Administrator").build();
        when(authRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(role));

        List<RoleDto> roles = adminRbacService.listRoles();

        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).getRoleCode()).isEqualTo("ADMIN");
    }

    @Test
    void shouldAssignRole() {
        AuthUser user = AuthUser.builder().authUserId(1L).build();
        AuthRole role = AuthRole.builder().authRoleId(1).build();
        when(authUserMapper.selectById(1L)).thenReturn(user);
        when(authRoleMapper.selectById(1)).thenReturn(role);
        when(roleUserRelationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        adminRbacService.assignRole(1L, 1);

        verify(roleUserRelationMapper).insert(any(AuthRoleUserRelation.class));
    }

    @Test
    void shouldThrowWhenAssignDuplicateRole() {
        AuthUser user = AuthUser.builder().authUserId(1L).build();
        AuthRole role = AuthRole.builder().authRoleId(1).build();
        when(authUserMapper.selectById(1L)).thenReturn(user);
        when(authRoleMapper.selectById(1)).thenReturn(role);
        when(roleUserRelationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> adminRbacService.assignRole(1L, 1))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldRemoveRole() {
        adminRbacService.removeRole(1L, 1);
        verify(roleUserRelationMapper).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    void shouldCreateAdminUser() {
        CreateAdminUserRequest req = new CreateAdminUserRequest();
        req.setUsername("newadmin");
        req.setEmail("admin@example.com");
        req.setPassword("Admin123456");
        when(authUserMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordService.hash(anyString())).thenReturn("hashed");

        AdminUserDto result = adminRbacService.createAdminUser(req);

        assertThat(result.getUsername()).isEqualTo("newadmin");
        verify(authUserMapper).insert(any(AuthUser.class));
    }

    @Test
    void shouldThrowWhenAdminUsernameExists() {
        CreateAdminUserRequest req = new CreateAdminUserRequest();
        req.setUsername("existing");
        req.setEmail("admin@example.com");
        req.setPassword("Admin123456");
        when(authUserMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> adminRbacService.createAdminUser(req))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldResetAdminPassword() {
        AuthUser user = AuthUser.builder().authUserId(1L).build();
        when(authUserMapper.selectById(1L)).thenReturn(user);
        when(passwordService.hash(anyString())).thenReturn("new-hashed");

        adminRbacService.resetAdminPassword(1L, "NewPass123");

        assertThat(user.getAuthUserPassword()).isEqualTo("new-hashed");
        verify(authUserMapper).updateById(user);
    }

    @Test
    void shouldThrowWhenResetPasswordUserNotFound() {
        when(authUserMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> adminRbacService.resetAdminPassword(99L, "NewPass123"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldListAdminUsers() {
        AuthUser user = AuthUser.builder()
                .authUserId(1L).authUserUsername("admin1")
                .authUserMail("admin1@example.com").authUserScope("ADMIN").build();
        when(authUserMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<AuthUser>(1, 10, 1).setRecords(List.of(user)));

        Page<AdminUserDto> result = adminRbacService.listAdminUsers(1, 10);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords().get(0).getUsername()).isEqualTo("admin1");
    }
}
