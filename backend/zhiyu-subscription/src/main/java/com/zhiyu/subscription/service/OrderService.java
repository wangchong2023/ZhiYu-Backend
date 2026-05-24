package com.zhiyu.subscription.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.subscription.converter.SubscriptionConverter;
import com.zhiyu.subscription.dto.CreateOrderRequest;
import com.zhiyu.subscription.dto.OrderDto;
import com.zhiyu.subscription.entity.PaymentRecord;
import com.zhiyu.subscription.entity.SubscriptionOrder;
import com.zhiyu.subscription.entity.SubscriptionPlan;
import com.zhiyu.subscription.mapper.PaymentRecordMapper;
import com.zhiyu.subscription.mapper.SubscriptionOrderMapper;
import com.zhiyu.subscription.mapper.SubscriptionPlanMapper;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_REFUNDED = "REFUNDED";
    private static final String PAYMENT_SUCCESS = "SUCCESS";
    private static final String ORDER_SEQ_KEY = "order:seq:";
    private static final int ORDER_SEQ_LENGTH = 6;

    private final SubscriptionOrderMapper orderMapper;
    private final SubscriptionPlanMapper planMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final SubscriptionService subscriptionService;
    private final StringRedisTemplate redisTemplate;

    @Transactional(rollbackFor = Exception.class)
    public OrderDto createOrder(final CreateOrderRequest request, final Long userId) {
        SubscriptionPlan plan = planMapper.selectById(request.getPlanId());
        if (plan == null || plan.getIsActive() == 0) {
            throw new BizException(BizErrorCode.PLAN_NOT_EXIST);
        }

        int amount;
        if ("YEARLY".equals(request.getPeriod())) {
            amount = plan.getPriceYearly();
        } else {
            amount = plan.getPriceMonthly();
        }

        if (amount == 0) {
            throw new BizException(BizErrorCode.PLAN_NOT_EXIST);
        }

        String orderNo = generateOrderNo();

        SubscriptionOrder order = SubscriptionOrder.builder()
                .orderNo(orderNo)
                .userId(userId)
                .planId(plan.getId())
                .planKey(plan.getPlanKey())
                .period(request.getPeriod())
                .amount(amount)
                .originalAmount(amount)
                .currency("CNY")
                .channel(request.getChannel())
                .status(STATUS_PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        orderMapper.insert(order);

        log.info("Order created: orderNo={}, userId={}, planKey={}, amount={}",
                orderNo, userId, plan.getPlanKey(), amount);
        return SubscriptionConverter.INSTANCE.toOrderDto(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderDto payOrder(final String orderNo, final Long userId) {
        SubscriptionOrder order = orderMapper.selectOne(
                new LambdaQueryWrapper<SubscriptionOrder>()
                        .eq(SubscriptionOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(BizErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(BizErrorCode.ACCESS_DENIED);
        }
        if (!STATUS_PENDING.equals(order.getStatus())) {
            throw new BizException(BizErrorCode.ORDER_ALREADY_PAID);
        }

        String transactionId = generateTransactionId(order.getChannel());
        LocalDateTime now = LocalDateTime.now();

        order.setStatus(STATUS_PAID);
        order.setTransactionId(transactionId);
        order.setPaidAt(now);
        order.setUpdatedAt(now);
        orderMapper.updateById(order);

        PaymentRecord record = PaymentRecord.builder()
                .orderId(order.getId())
                .userId(userId)
                .channel(order.getChannel())
                .transactionId(transactionId)
                .amount(order.getAmount())
                .currency(order.getCurrency())
                .status(PAYMENT_SUCCESS)
                .paidAt(now)
                .createdAt(now)
                .build();
        paymentRecordMapper.insert(record);

        subscriptionService.activateSubscription(userId, order.getPlanId(), order.getPeriod());

        log.info("Order paid: orderNo={}, userId={}, transactionId={}", orderNo, userId, transactionId);
        return SubscriptionConverter.INSTANCE.toOrderDto(order);
    }

    private String generateOrderNo() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String seqKey = ORDER_SEQ_KEY + datePart;
        Long seq = redisTemplate.opsForValue().increment(seqKey);
        if (seq == null || seq == 1) {
            redisTemplate.expire(seqKey, java.time.Duration.ofDays(1));
        }
        return "ZY" + datePart + String.format("%0" + ORDER_SEQ_LENGTH + "d",
                seq != null ? seq : 1);
    }

    private String generateTransactionId(final String channel) {
        String prefix;
        switch (channel) {
            case "WECHAT":
                prefix = "WXP";
                break;
            case "ALIPAY":
                prefix = "ALP";
                break;
            case "APPLE":
                prefix = "IAP";
                break;
            case "GOOGLE":
                prefix = "GPA";
                break;
            default:
                prefix = "TXN";
                break;
        }
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
