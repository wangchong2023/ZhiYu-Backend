package com.zhiyu.ufp.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.ufp.auth.entity.AuthRole;
import com.zhiyu.ufp.auth.entity.AuthRoleUserRelation;
import com.zhiyu.ufp.auth.mapper.AuthRoleMapper;
import com.zhiyu.ufp.auth.mapper.AuthRoleUserRelationMapper;
import com.zhiyu.ufp.common.datasource.UfpDS;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@UfpDS("ufp_auth")
public class AuthRoleService implements IAuthRoleService {

    private final AuthRoleMapper authRoleMapper;
    private final AuthRoleUserRelationMapper roleUserRelationMapper;

    @Override
    public List<AuthRole> selectList(final LambdaQueryWrapper<AuthRole> wrapper) {
        return authRoleMapper.selectList(wrapper);
    }

    @Override
    public AuthRole selectById(final Integer roleId) {
        return authRoleMapper.selectById(roleId);
    }

    @Override
    public long selectRelationCount(final LambdaQueryWrapper<AuthRoleUserRelation> wrapper) {
        return roleUserRelationMapper.selectCount(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int insertRelation(final AuthRoleUserRelation rel) {
        return roleUserRelationMapper.insert(rel);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int deleteRelation(final LambdaQueryWrapper<AuthRoleUserRelation> wrapper) {
        return roleUserRelationMapper.delete(wrapper);
    }
}
