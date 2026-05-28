/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: SubscriptionConverter.java
 * 创建时间: 2026-05-28
 * 描述: 订阅模块实体转换器接口，使用 MapStruct 框架自动生成高性能的领域实体与数据传输对象 (DTO) 的互转代码。
 */
package com.zhiyu.subscription.converter;

import com.zhiyu.subscription.dto.OrderDto;
import com.zhiyu.subscription.dto.PlanDto;
import com.zhiyu.subscription.dto.SubscriptionDto;
import com.zhiyu.subscription.entity.SubscriptionOrder;
import com.zhiyu.subscription.entity.SubscriptionPlan;
import com.zhiyu.subscription.entity.UserSubscription;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 接口名: SubscriptionConverter
 * 描述: 订阅模块的模型映射转换接口。使用 MapStruct 编译期注解处理器生成实现类，
 * 比反射机制（如 BeanUtils）具有极高的运行时性能和类型安全保证。
 */
@Mapper
public interface SubscriptionConverter {

    /**
     * 实例单例来源，全局统一消费入口
     */
    SubscriptionConverter INSTANCE = Mappers.getMapper(SubscriptionConverter.class);

    /**
     * 描述: 将订阅套餐数据库实体转换成 DTO 传输对象
     * @param entity 订阅套餐领域实体
     * @return 映射后的套餐展示 DTO
     */
    PlanDto toPlanDto(SubscriptionPlan entity);

    /**
     * 描述: 将订阅订单数据库实体转换成 DTO 传输对象
     * @param entity 订阅订单领域实体
     * @return 映射后的订单展示 DTO
     */
    OrderDto toOrderDto(SubscriptionOrder entity);

    /**
     * 描述: 将用户个人订阅的数据库实体转换成 DTO 传输对象。
     * 特别注意：由于 planName（套餐名称）属性属于关联表的非直接物理字段，在此声明显式忽略，
     * 避开 MapStruct 的编译期 Unmapped target property 警告。
     * @param entity 用户物理订阅领域实体
     * @return 映射后的个人订阅详情 DTO
     */
    @Mapping(target = "planName", ignore = true)
    SubscriptionDto toSubscriptionDto(UserSubscription entity);
}

