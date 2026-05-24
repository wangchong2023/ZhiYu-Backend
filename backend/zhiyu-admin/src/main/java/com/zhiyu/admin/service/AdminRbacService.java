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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminRbacService {

    private static final int ERR_USER_NOT_FOUND = 40401;
    private static final int ERR_ROLE_NOT_FOUND = 40402;
    private static final int ERR_ALREADY_ASSIGNED = 41404;
    private static final int ERR_ADMIN_EXISTS = 41405;

    private final AuthRoleMapper authRoleMapper;
    private final AuthRoleUserRelationMapper roleUserRelationMapper;
    private final AuthUserMapper authUserMapper;
    private final PasswordService passwordService;

    public List<RoleDto> listRoles() {
        return authRoleMapper.selectList(new LambdaQueryWrapper<AuthRole>()
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
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ERR_USER_NOT_FOUND, "User not found");
        }
        AuthRole role = authRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(ERR_ROLE_NOT_FOUND, "Role not found");
        }

        Long count = roleUserRelationMapper.selectCount(
                new LambdaQueryWrapper<AuthRoleUserRelation>()
                        .eq(AuthRoleUserRelation::getAuthUserId, userId)
                        .eq(AuthRoleUserRelation::getAuthRoleId, roleId));
        if (count > 0) {
            throw new BizException(ERR_ALREADY_ASSIGNED, "Role already assigned to user");
        }

        AuthRoleUserRelation rel = AuthRoleUserRelation.builder()
                .authRoleId(roleId)
                .authUserId(userId)
                .build();
        roleUserRelationMapper.insert(rel);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeRole(final Long userId, final Integer roleId) {
        roleUserRelationMapper.delete(
                new LambdaQueryWrapper<AuthRoleUserRelation>()
                        .eq(AuthRoleUserRelation::getAuthUserId, userId)
                        .eq(AuthRoleUserRelation::getAuthRoleId, roleId));
    }

    public Page<AdminUserDto> listAdminUsers(final int page, final int size) {
        var wrapper = new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getAuthUserScope, "ADMIN")
                .orderByDesc(AuthUser::getCreatedTime);

        Page<AuthUser> entityPage = authUserMapper.selectPage(new Page<>(page, size), wrapper);
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
        Long count = authUserMapper.selectCount(
                new LambdaQueryWrapper<AuthUser>()
                        .eq(AuthUser::getAuthUserUsername, request.getUsername()));
        if (count > 0) {
            throw new BizException(ERR_ADMIN_EXISTS, "Username already exists");
        }

        AuthUser user = AuthUser.builder()
                .authUserUsername(request.getUsername())
                .authUserMail(request.getEmail())
                .authUserPassword(passwordService.hash(request.getPassword()))
                .authUserCode(UUID.randomUUID().toString().replace("-", ""))
                .authUserScope("ADMIN")
                .authUserEnable(1)
                .build();
        authUserMapper.insert(user);

        return AdminUserDto.builder()
                .userId(user.getAuthUserId())
                .username(user.getAuthUserUsername())
                .email(user.getAuthUserMail())
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetAdminPassword(final Long userId, final String newPassword) {
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ERR_USER_NOT_FOUND, "User not found");
        }
        user.setAuthUserPassword(passwordService.hash(newPassword));
        authUserMapper.updateById(user);
    }
}
