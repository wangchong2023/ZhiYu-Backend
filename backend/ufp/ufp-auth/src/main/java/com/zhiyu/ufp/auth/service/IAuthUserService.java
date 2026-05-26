/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: IAuthUserService.java
 * 创建时间: 2026-05-27
 * 描述: 统一认证平台的用户基础服务接口，定义跨模块调用的用户核心契约，支持动态数据源路由。
 */
package com.zhiyu.ufp.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.common.annotation.UfpClient;

import java.util.Collection;
import java.util.List;

/**
 * 接口名: IAuthUserService
 * 描述: 统一用户管理契约接口。声明了对 AuthUser 实体进行主键、批量及分页查询和增删改的核心能力，支持 Feign 或本地注入代理。
 */
@UfpClient(name = "ufp-auth", path = "/api/v1/ufp/auth/users")
public interface IAuthUserService {

    /**
     * 描述: 根据主键 ID 查询用户实体
     * @param id 用户唯一标识 ID
     * @return 查询出的用户实体，若不存在返回 null
     */
    AuthUser selectById(Long id);

    /**
     * 描述: 根据一批主键 ID 批量查询用户实体列表
     * @param ids 用户 ID 集合
     * @return 符合条件的用户实体列表
     */
    List<AuthUser> selectByIds(Collection<Long> ids);

    /**
     * 描述: 根据条件构造器查询单个用户实体
     * @param wrapper Lambda查询条件构造器
     * @return 符合查询条件的单个用户实体
     */
    AuthUser selectOne(LambdaQueryWrapper<AuthUser> wrapper);

    /**
     * 描述: 根据条件构造器分页查询用户实体列表
     * @param page 分页配置对象
     * @param wrapper Lambda查询条件构造器
     * @return 分页包装后的用户实体结果
     */
    Page<AuthUser> selectPage(Page<AuthUser> page, LambdaQueryWrapper<AuthUser> wrapper);

    /**
     * 描述: 根据条件构造器查询符合条件的用户总数
     * @param wrapper Lambda查询条件构造器
     * @return 符合条件的用户数量
     */
    long selectCount(LambdaQueryWrapper<AuthUser> wrapper);

    /**
     * 描述: 插入一条新的用户记录
     * @param user 用户实体对象
     * @return 影响的数据库行数
     */
    int insert(AuthUser user);

    /**
     * 描述: 根据主键 ID 更新用户记录
     * @param user 待更新的用户实体对象（需含 ID）
     * @return 影响的数据库行数
     */
    int updateById(AuthUser user);
}

