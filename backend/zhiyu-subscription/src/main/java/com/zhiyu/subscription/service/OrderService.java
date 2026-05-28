/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

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
import java.util.Locale;
import java.util.UUID;

/**
 * 智宇平台订阅订单核心业务服务。
 *
 * <p>主要处理订阅订单的生命周期管理，包括结合 Redis 生成全局唯一订单号的订单创建事务、
 * 订单模拟支付确认、记录流水详情、以及在支付完成后联动激活或展期用户订阅权益等核心业务逻辑。</p>
 *
 * @author ZhiYu Architect
 * @version 1.0.0
 * @since 2026-05-28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    /** 订单状态：待支付 */
    private static final String STATUS_PENDING = "PENDING";
    /** 订单状态：已支付 */
    private static final String STATUS_PAID = "PAID";
    /** 订单状态：已退款 */
    private static final String STATUS_REFUNDED = "REFUNDED";
    /** 支付状态：支付成功 */
    private static final String PAYMENT_SUCCESS = "SUCCESS";
    /** Redis 自增订单号前缀 */
    private static final String ORDER_SEQ_KEY = "order:seq:";
    /** 订单自增流水号位数 */
    private static final int ORDER_SEQ_LENGTH = 6;
    /** 交易流水号的 UUID 随机后缀截取长度 */
    private static final int TXN_UUID_SUFFIX_LEN = 8;

    private final SubscriptionOrderMapper orderMapper;
    private final SubscriptionPlanMapper planMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final SubscriptionService subscriptionService;
    private final StringRedisTemplate redisTemplate;

    /**
     * 创建订阅订单记录。
     *
     * <p>检验套餐及其有效性，按照订购周期计算实际应付金额，并为用户生成包含待支付状态的本地订单记录。</p>
     *
     * @param request 包含目标套餐和订购周期的请求参数模型
     * @param userId 发起订单创建的当前登录用户主键 ID
     * @return 已持久化保存并封装好的订单数据传输对象 DTO
     * @throws BizException 当指定的订阅套餐不存在或处于未激活/停用状态时，抛出对应的业务异常
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderDto createOrder(final CreateOrderRequest request, final Long userId) {
        // 1. 查询并校验订阅套餐的存在性及激活状态
        SubscriptionPlan plan = planMapper.selectById(request.getPlanId());
        if (plan == null || plan.getIsActive() == 0) {
            throw new BizException(BizErrorCode.PLAN_NOT_EXIST);
        }

        // 2. 根据计费周期（按月/按年）计算实付金额
        int amount;
        if ("YEARLY".equals(request.getPeriod())) {
            amount = plan.getPriceYearly();
        } else {
            amount = plan.getPriceMonthly();
        }

        // 3. 拦截单价异常（如未配置定价）的套餐
        if (amount == 0) {
            throw new BizException(BizErrorCode.PLAN_NOT_EXIST);
        }

        // 4. 利用 Redis 自增机制生成全局唯一的订单流水号
        String orderNo = generateOrderNo();

        // 5. 组装并保存待支付订单实体
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

        // 6. 记录订单创建审计日志
        if (log.isInfoEnabled()) {
            log.info("Order created: orderNo={}, userId={}, planKey={}, amount={}",
                    orderNo, userId, plan.getPlanKey(), amount);
        }
        return SubscriptionConverter.INSTANCE.toOrderDto(order);
    }

    /**
     * 确认并模拟处理指定订单的支付流程。
     *
     * <p>执行多项边界防御检查：校验订单是否存在、订单是否归属当前操作用户、订单是否重复支付。
     * 随后生成第三方交易流水号，将订单流转为已支付，写入支付详情流水，并触发激活对应的订阅权益。</p>
     *
     * @param orderNo 待确认支付的订单流水号
     * @param userId 发起支付确认的用户 ID（需与订单所有者完全一致）
     * @return 支付确认流转后的订单详情 DTO
     * @throws BizException 当订单不存在、操作用户与所有者不一致、或订单此前已支付完毕时抛出对应的业务异常
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderDto payOrder(final String orderNo, final Long userId) {
        // 1. 根据订单号检索订单信息
        SubscriptionOrder order = orderMapper.selectOne(
                new LambdaQueryWrapper<SubscriptionOrder>()
                        .eq(SubscriptionOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(BizErrorCode.ORDER_NOT_FOUND);
        }
        
        // 2. 防御性鉴权：严禁非订单所有者用户越权执行支付
        if (!order.getUserId().equals(userId)) {
            throw new BizException(BizErrorCode.ACCESS_DENIED);
        }
        
        // 3. 幂等校验：防止对非待支付状态订单重复扣款或模拟支付
        if (!STATUS_PENDING.equals(order.getStatus())) {
            throw new BizException(BizErrorCode.ORDER_ALREADY_PAID);
        }

        // 4. 生成渠道专属的唯一外部交易流水号
        String transactionId = generateTransactionId(order.getChannel());
        LocalDateTime now = LocalDateTime.now();

        // 5. 更新订单状态为已支付，并记录交易凭证
        order.setStatus(STATUS_PAID);
        order.setTransactionId(transactionId);
        order.setPaidAt(now);
        order.setUpdatedAt(now);
        orderMapper.updateById(order);

        // 6. 持久化保存物理支付记录明细以备对账
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

        // 7. 联动激活用户的对应订阅权益（更新订阅周期及赋予平台使用额度）
        subscriptionService.activateSubscription(userId, order.getPlanId(), order.getPeriod());

        log.info("Order paid: orderNo={}, userId={}, transactionId={}", orderNo, userId, transactionId);
        return SubscriptionConverter.INSTANCE.toOrderDto(order);
    }

    /**
     * 利用 Redis 递增发生器生成唯一的业务订单号。
     *
     * <p>命名规范为：ZY + 年月日 (yyyyMMdd) + 6 位按天自增序列值。例如：ZY20260528000001。</p>
     *
     * @return 业务层全局唯一的订单号字符串
     */
    private String generateOrderNo() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String seqKey = ORDER_SEQ_KEY + datePart;
        
        // 利用 Redis 的 INCR 操作保证多实例部署下的并发自增原子性
        Long seq = redisTemplate.opsForValue().increment(seqKey);
        
        // 若当前自增键为首次创建（值为 1），则必须设置 24 小时的生存时间自动释放 Redis 内存
        if (seq == null || seq == 1) {
            redisTemplate.expire(seqKey, java.time.Duration.ofDays(1));
        }
        
        return "ZY" + datePart + String.format("%0" + ORDER_SEQ_LENGTH + "d",
                seq != null ? seq : 1);
    }

    /**
     * 根据支付渠道和时间戳序列生成唯一的虚拟交易流水号。
     *
     * @param channel 支付通道（如 WECHAT, ALIPAY, APPLE, GOOGLE 等）
     * @return 全局唯一的外部交易流水号字符串
     */
    private String generateTransactionId(final String channel) {
        String prefix;
        // 1. 根据三方平台支付通道分配专属的前缀缩写
        switch (channel) {
            case "WECHAT":
                prefix = "WXP"; // WeChat Pay
                break;
            case "ALIPAY":
                prefix = "ALP"; // AliPay
                break;
            case "APPLE":
                prefix = "IAP"; // In-App Purchase
                break;
            case "GOOGLE":
                prefix = "GPA"; // Google Play Authentication
                break;
            default:
                prefix = "TXN"; // 通用交易 Transaction
                break;
        }
        // 2. 拼接当前时间戳和 UUID 后缀，转换为大写返回，保证绝对唯一且有可追溯的时间属性
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().replace("-", "")
                        .substring(0, TXN_UUID_SUFFIX_LEN).toUpperCase(Locale.ROOT);
    }
}
