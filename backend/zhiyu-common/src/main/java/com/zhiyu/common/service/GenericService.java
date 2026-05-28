/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

package com.zhiyu.common.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 智宇平台 MyBatis-Plus 服务公共分页与批量加载辅助工具类。
 *
 * <p>所有工具方法均声明为静态方法，服务类可直接调用，无需更改其继承体系。
 * 针对需要完整 IService API 支持的业务服务，建议直接继承
 * {@link com.baomidou.mybatisplus.extension.service.impl.ServiceImpl}。</p>
 *
 * @author ZhiYu Architect
 * @version 1.0.0
 * @since 2026-05-28
 */
public final class GenericService {

    /**
     * 私有构造函数，防止工具类被实例化。
     */
    private GenericService() { /* 静态工具类 */ }

    /**
     * 将 MyBatis-Plus 实体分页对象 {@link Page} 转换为 DTO 分页对象 {@link Page}，
     * 并使用提供的转换函数转换记录数据。
     *
     * <p>本方法旨在替换以下重复的三步转换模式：
     * <pre>{@code
     * Page<E> entityPage = mapper.selectPage(new Page<>(page, size), wrapper);
     * Page<D> dtoPage = new Page<>(page, size, entityPage.getTotal());
     * dtoPage.setRecords(entityPage.getRecords().stream().map(converter).toList());
     * }</pre>
     * 简化为单次调用：{@code GenericService.pageDto(entityPage, converter)}。</p>
     *
     * @param entityPage MyBatis-Plus 实体分页源数据对象
     * @param converter 实体到 DTO 的数据转换函数
     * @param <T> 实体对象类型
     * @param <D> DTO 目标对象类型
     * @return 填充了转换后记录的全新 DTO 分页对象
     */
    public static <T, D> Page<D> pageDto(final IPage<T> entityPage,
                                          final Function<T, D> converter) {
        // 1. 初始化目标 DTO 分页对象，拷贝当前页码、每页大小及总记录数
        Page<D> dtoPage = new Page<>(entityPage.getCurrent(),
                entityPage.getSize(), entityPage.getTotal());
        
        // 2. 流式映射处理，将实体记录集转换为 DTO 记录集
        List<D> records = entityPage.getRecords().stream()
                .map(converter)
                .collect(Collectors.toList());
        
        // 3. 设置转换后的结果集并返回
        dtoPage.setRecords(records);
        return dtoPage;
    }

    /**
     * 通过单次查询根据一组 ID 批量获取实体，并将其转换为以实体自然键为键的只读 Map。
     *
     * <p>本方法可有效消除在循环中逐条查询 {@code mapper.selectById(id)} 的 N+1 数据库性能隐患：
     * <pre>{@code
     * Map<Long, User> users = GenericService.batchFetchOne(userIds, batch -> {
     *     return userMapper.selectBatchIds(batch);
     * }, User::getId);
     * }</pre></p>
     *
     * @param ids 需要查询的 ID 集合
     * @param batchFetcher 传入批量 ID 集合并返回对应实体列表的批量查询函数
     * @param keyExtractor 从实体对象中提取 Map 键值的属性提取函数（例如主键或唯一自然键）
     * @param <K> Map 键（Key）的泛型类型
     * @param <V> Map 值（Value）及实体的泛型类型
     * @return 包含查询结果的、以 Key 值为索引的只读 Map 映射表。若传入 ID 集合为空或查询无匹配记录，则返回空 Map。
     */
    public static <K, V> Map<K, V> batchFetchOne(
            final java.util.Collection<K> ids,
            final Function<java.util.Collection<K>, List<V>> batchFetcher,
            final Function<V, K> keyExtractor) {
        // 1. 防御性空校验：若 ID 集合为空，则直接返回空只读 Map 规避数据库访问
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        
        // 2. 执行批量数据库查询函数
        List<V> results = batchFetcher.apply(ids);
        
        // 3. 校验查询结果：若无匹配数据则返回空只读 Map
        if (results == null || results.isEmpty()) {
            return Collections.emptyMap();
        }
        
        // 4. 将列表转换为以 keyExtractor 提取值为键的 Map，对键冲突采取保留首项的兜底策略，最终封装为只读视图
        return Collections.unmodifiableMap(
                results.stream()
                        .collect(Collectors.toMap(keyExtractor, Function.identity(), (a, b) -> a)));
    }
}
