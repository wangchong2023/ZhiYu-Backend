package com.zhiyu.ufp.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.ufp.auth.entity.AuthRole;
import com.zhiyu.ufp.auth.entity.AuthRoleUserRelation;
import com.zhiyu.ufp.common.annotation.UfpClient;

import java.util.List;

@UfpClient(name = "ufp-auth", path = "/api/v1/ufp/auth/roles")
public interface IAuthRoleService {

    List<AuthRole> selectList(LambdaQueryWrapper<AuthRole> wrapper);

    AuthRole selectById(Integer roleId);

    long selectRelationCount(LambdaQueryWrapper<AuthRoleUserRelation> wrapper);

    int insertRelation(AuthRoleUserRelation rel);

    int deleteRelation(LambdaQueryWrapper<AuthRoleUserRelation> wrapper);
}
