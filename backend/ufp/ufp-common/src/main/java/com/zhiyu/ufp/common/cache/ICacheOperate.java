/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

package com.zhiyu.ufp.common.cache;

import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * 智宇平台通用缓存操作 SPI 接口。
 *
 * <p>本接口为不同的底层缓存后端（如本地 JVM 堆内存的 Caffeine、分布式的 Redis/Redisson）提供高内聚、
 * 统一的操作层抽象。消费端业务模块仅声明依赖此接口，底层具体缓存实现由依赖包在运行时根据 Classpath 
 * 环境自动装配和替换。</p>
 *
 * @author ZhiYu Architect
 * @version 1.0.0
 * @since 2026-05-28
 */
public interface ICacheOperate {

    /**
     * 获取当前缓存服务提供商的逻辑名称。
     *
     * @return 缓存组件名称标识，例如 "local" (本地堆缓存)、"redisson" (分布式缓存)
     */
    String name();

    /**
     * 获取当前缓存提供商是否应被视作系统默认缓存。
     *
     * <p>当系统运行时环境仅检测到唯一一个缓存实现类时，应重写此方法并返回 {@code true}。</p>
     *
     * @return 若为默认缓存实现则返回 {@code true}，否则返回 {@code false}（默认为 {@code false}）
     */
    default boolean defaultCache() {
        return false;
    }

    /**
     * 获取底层缓存组件的原生客户端对象。
     *
     * <p>提供直接获取底层原生实例的能力（如 Caffeine 的 {@code Cache} 或 Redisson 的 {@code RedissonClient}），
     * 适用于某些 SPI 接口未声明、但业务又亟需的底层高级/专属操作。</p>
     *
     * @return 底层原生的缓存客户端实例
     */
    Object getNative();

    /**
     * 根据指定的键从缓存中检索并读取数据。
     *
     * @param name 缓存对象的唯一键（Key）
     * @param <T> 期望返回的缓存数据泛型类型
     * @return 缓存中对应的值，若缓存失效、过期或不存在则返回 {@code null}
     */
    <T> T get(String name);

    /**
     * 将数据存入缓存，并赋予系统默认的生存时间（TTL）。
     *
     * @param name 缓存对象的唯一键（Key）
     * @param value 待写入的缓存数据值对象
     */
    void set(String name, Object value);

    /**
     * 将数据存入缓存，并赋予明确指定的生存时间（TTL）与时间单位。
     *
     * @param name 缓存对象的唯一键（Key）
     * @param value 待写入的缓存数据值对象
     * @param timeToLive 缓存数据的生存时长数值
     * @param timeUnit 生存时长数值对应的 {@link TimeUnit} 时间单位（如 SECONDS, MINUTES 等）
     */
    void set(String name, Object value, long timeToLive, TimeUnit timeUnit);

    /**
     * 从缓存中彻底删除单个指定的缓存条目。
     *
     * @param name 缓存对象的唯一键（Key）
     */
    void delete(String name);

    /**
     * 根据通配符模式匹配，批量删除符合条件的所有缓存条目。
     *
     * <p>通配符的匹配语法由底层缓存的实现机制所决定（例如支持 glob 表达式或正则表达式等）。</p>
     *
     * @param pattern 筛选缓存键的匹配表达式
     */
    void deletes(String pattern);

    /**
     * 批量删除一组显式指定的缓存键。
     *
     * @param keys 需要删除的缓存键数组或可变参数列表
     */
    void delete(String... keys);

    /**
     * 检索并返回所有匹配指定通配符模式的缓存键集合。
     *
     * @param pattern 筛选缓存键的匹配表达式
     * @param <T> 键的泛型类型
     * @return 匹配成功的所有缓存键集合，若无匹配项则返回空集合，绝不返回 {@code null}
     */
    <T> Collection<T> keys(String pattern);

    /**
     * 从底层分布式缓存中获取一个分布式队列视图 {@link Queue}。
     *
     * <p>注：在本地单机内存缓存（如 Caffeine）的实现中调用此方法，可能会抛出 {@link UnsupportedOperationException} 异常。</p>
     *
     * @param name 分布式队列的唯一标识键
     * @return 分布式队列视图实例
     */
    Queue<?> getQueue(String name);

    /**
     * 从底层分布式缓存中获取一个分布式哈希 Map 视图。
     *
     * @param name 分布式 Map 的唯一标识键
     * @param <K> Map 键的泛型类型
     * @param <V> Map 值的泛型类型
     * @return 分布式 Map 视图实例
     */
    <K, V> Map<K, V> getMap(String name);

    /**
     * 获取指定键的分布式并发锁 {@link Lock}。
     *
     * @param name 分布式锁的唯一标识键名
     * @param fair 是否启用公平锁机制
     * @return 分布式互斥锁实例
     */
    Lock getLock(String name, boolean fair);
}
