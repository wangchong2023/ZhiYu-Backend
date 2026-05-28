/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: AuthUserService.java
 * 创建时间: 2026-05-27
 * 描述: 统一认证用户管理服务实现类，利用 MyBatis-Plus 进行持久层访问，通过注解声明路由到 ufp_auth 平台数据库。
 */
package com.zhiyu.ufp.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.common.datasource.UfpDS;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * 类名: AuthUserService
 * 描述: 统一用户管理服务实现。类上标注了 `@UfpDS("ufp_auth")` 注解，由动态数据源切面拦截，使得该类的所有方法在运行时切换至平台认证库。
 */
@Service
@RequiredArgsConstructor
@UfpDS("ufp_auth")
public class AuthUserService implements IAuthUserService {

    private final AuthUserMapper authUserMapper;

    /**
     * 描述: 根据主键 ID 查询用户实体
     * @param id 用户唯一标识 ID
     * @return 查询出的用户实体，不存在返回 null
     */
    @Override
    public AuthUser selectById(final Long id) {
        return authUserMapper.selectById(id);
    }

    /**
     * 描述: 根据一批主键 ID 批量查询用户实体列表
     * @param ids 用户 ID 集合
     * @return 符合条件的用户实体列表
     */
    @Override
    public List<AuthUser> selectByIds(final Collection<Long> ids) {
        // 防御性编程：若传入集合为空，直接返回空列表，避免引发 SQL 语法错误
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return authUserMapper.selectList(new LambdaQueryWrapper<AuthUser>()
                .in(AuthUser::getAuthUserId, ids));
    }

    /**
     * 描述: 根据条件构造器查询单个用户实体
     * @param wrapper Lambda查询条件构造器
     * @return 符合查询条件的单个用户实体
     */
    @Override
    public AuthUser selectOne(final LambdaQueryWrapper<AuthUser> wrapper) {
        return authUserMapper.selectOne(wrapper);
    }

    /**
     * 描述: 根据条件构造器分页查询用户实体列表
     * @param page 分页配置对象
     * @param wrapper Lambda查询条件构造器
     * @return 分页包装后的用户实体结果
     */
    @Override
    public Page<AuthUser> selectPage(final Page<AuthUser> page,
                                      final LambdaQueryWrapper<AuthUser> wrapper) {
        return authUserMapper.selectPage(page, wrapper);
    }

    /**
     * 描述: 根据条件构造器查询符合条件的用户总数
     * @param wrapper Lambda查询条件构造器
     * @return 符合条件的用户数量
     */
    @Override
    public long selectCount(final LambdaQueryWrapper<AuthUser> wrapper) {
        return authUserMapper.selectCount(wrapper);
    }

    /**
     * 描述: 插入一条新的用户记录
     * @param user 用户实体对象
     * @return 影响的数据库行数
     */
    @Override
    public int insert(final AuthUser user) {
        return authUserMapper.insert(user);
    }

    /**
     * 描述: 根据主键 ID 更新用户记录
     * @param user 待更新的用户实体对象（需含 ID）
     * @return 影响的数据库行数
     */
    @Override
    public int updateById(final AuthUser user) {
        return authUserMapper.updateById(user);
    }

    /**
     * 描述: 更新指定用户的偏好语言设置，并持久化写入数据库。
     * @param userId 用户的物理主键 ID
     * @param locale 偏好的语言标识，例如 "zh_CN" 或 "en_US"
     * @return 影响的数据库行数，若用户不存在抛出异常
     */
    @Override
    public int updatePersonalLocale(final Long userId, final String locale) {
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) {
            throw new com.zhiyu.ufp.common.exception.BizException(
                    com.zhiyu.ufp.common.exception.BizErrorCode.RESOURCE_NOT_FOUND);
        }
        user.setAuthUserPersonalLocale(locale);
        return authUserMapper.updateById(user);
    }
}

