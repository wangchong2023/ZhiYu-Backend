package com.zhiyu.subscription.converter;

import com.zhiyu.subscription.dto.OrderDto;
import com.zhiyu.subscription.dto.PlanDto;
import com.zhiyu.subscription.dto.SubscriptionDto;
import com.zhiyu.subscription.entity.SubscriptionOrder;
import com.zhiyu.subscription.entity.SubscriptionPlan;
import com.zhiyu.subscription.entity.UserSubscription;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface SubscriptionConverter {

    SubscriptionConverter INSTANCE = Mappers.getMapper(SubscriptionConverter.class);

    PlanDto toPlanDto(SubscriptionPlan entity);

    OrderDto toOrderDto(SubscriptionOrder entity);

    SubscriptionDto toSubscriptionDto(UserSubscription entity);
}
