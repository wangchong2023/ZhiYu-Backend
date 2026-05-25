package com.zhiyu.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.dto.AdminUserDto;
import com.zhiyu.admin.dto.CreateAdminUserRequest;
import com.zhiyu.admin.dto.RoleDto;
import com.zhiyu.ufp.auth.entity.AuthRole;
import com.zhiyu.ufp.auth.entity.AuthRoleUserRelation;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.oauth.OAuthField;
import com.zhiyu.ufp.auth.password.PasswordService;
import com.zhiyu.ufp.auth.service.AuthRoleService;
import com.zhiyu.ufp.auth.service.AuthUserService;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminRbacService {

    private final AuthRoleService authRoleService;
    private final AuthUserService authUserService;
    private final PasswordService passwordService;

    public List<RoleDto> listRoles() {
        return authRoleService.selectList(new LambdaQueryWrapper<AuthRole>()
                        .eq(AuthRole::getAuthRoleEnable, 1))
                .stream()
                .map(r -> RoleDto.builder()
                        .roleId(r.getAuthRoleId())
                        .roleName(r.getAuthRoleName())
                        .roleCode(r.getAuthRoleCode())
                        .enabled(r.getAuthRoleEnable())
                        .description(r.getAuthRoleDesc())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void assignRole(final Long userId, final Integer roleId) {
        AuthUser user = authUserService.selectById(userId);
        if (user == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }
        AuthRole role = authRoleService.selectById(roleId);
        if (role == null) {
            throw new BizException(BizErrorCode.ROLE_NOT_FOUND);
        }

        long count = authRoleService.selectRelationCount(
                new LambdaQueryWrapper<AuthRoleUserRelation>()
                        .eq(AuthRoleUserRelation::getAuthUserId, userId)
                        .eq(AuthRoleUserRelation::getAuthRoleId, roleId));
        if (count > 0) {
            throw new BizException(BizErrorCode.ROLE_ALREADY_ASSIGNED);
        }

        AuthRoleUserRelation rel = AuthRoleUserRelation.builder()
                .authRoleId(roleId)
                .authUserId(userId)
                .build();
        authRoleService.insertRelation(rel);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeRole(final Long userId, final Integer roleId) {
        authRoleService.deleteRelation(
                new LambdaQueryWrapper<AuthRoleUserRelation>()
                        .eq(AuthRoleUserRelation::getAuthUserId, userId)
                        .eq(AuthRoleUserRelation::getAuthRoleId, roleId));
    }

    public Page<AdminUserDto> listAdminUsers(final int page, final int size) {
        var wrapper = new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getAuthUserScope, OAuthField.SCOPE_ADMIN)
                .orderByDesc(AuthUser::getCreatedTime);

        Page<AuthUser> entityPage = authUserService.selectPage(new Page<>(page, size), wrapper);
        Page<AdminUserDto> dtoPage = new Page<>(page, size, entityPage.getTotal());
        dtoPage.setRecords(entityPage.getRecords().stream()
                .map(u -> AdminUserDto.builder()
                        .userId(u.getAuthUserId())
                        .username(u.getAuthUserUsername())
                        .email(u.getAuthUserMail())
                        .build())
                .collect(Collectors.toList()));
        return dtoPage;
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminUserDto createAdminUser(final CreateAdminUserRequest request) {
        long count = authUserService.selectCount(
                new LambdaQueryWrapper<AuthUser>()
                        .eq(AuthUser::getAuthUserUsername, request.getUsername()));
        if (count > 0) {
            throw new BizException(BizErrorCode.ADMIN_EXISTS);
        }

        AuthUser user = AuthUser.builder()
                .authUserUsername(request.getUsername())
                .authUserMail(request.getEmail())
                .authUserPassword(passwordService.hash(request.getPassword()))
                .authUserCode(UUID.randomUUID().toString().replace("-", ""))
                .authUserScope(OAuthField.SCOPE_ADMIN)
                .authUserEnable(1)
                .build();
        authUserService.insert(user);

        return AdminUserDto.builder()
                .userId(user.getAuthUserId())
                .username(user.getAuthUserUsername())
                .email(user.getAuthUserMail())
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetAdminPassword(final Long userId, final String newPassword) {
        AuthUser user = authUserService.selectById(userId);
        if (user == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }
        user.setAuthUserPassword(passwordService.hash(newPassword));
        authUserService.updateById(user);
    }
}
