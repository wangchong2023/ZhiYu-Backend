/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: SentinelRulesInitializer.java
 * 创建时间: 2026-05-27
 * 描述: Sentinel 流控规则初始化器，在应用启动完成后加载限流规则。
 *       优先使用外部配置规则；若无外部配置，则加载内置默认规则作为兜底。
 */
package com.zhiyu.common.sentinel;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * 类名: SentinelRulesInitializer
 * 描述: 在应用上下文完全就绪后初始化 Sentinel 流控规则。
 *
 * <p>规则来源优先级：
 * <ol>
 *   <li>外部配置 {@link SentinelProperties} 中定义的规则（优先级最高）</li>
 *   <li>内置默认兜底规则（当外部配置为空时生效）：
 *     <ul>
 *       <li>登录接口 — 10 QPS</li>
 *       <li>短信发送 — 1 QPS</li>
 *       <li>注册接口 — 5 QPS</li>
 *       <li>通用 API — 100 QPS</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>当 Nacos 数据源已配置时，Nacos 推送的规则优先级高于此处注册的规则。
 * 此处规则作为 Nacos 不可用时的最后防线。
 */
@Slf4j
@RequiredArgsConstructor
public class SentinelRulesInitializer {

    /** 流控规则外部配置属性 */
    private final SentinelProperties properties;

    /** 登录接口默认限流阈值（QPS） */
    private static final double LOGIN_QPS = 10;
    /** 短信发送接口默认限流阈值（QPS） */
    private static final double SMS_QPS = 1;
    /** 注册接口默认限流阈值（QPS） */
    private static final double REGISTER_QPS = 5;
    /** 通用 API 默认限流阈值（QPS） */
    private static final double CATCH_ALL_QPS = 100;

    /**
     * 描述: 应用上下文完全就绪后初始化限流规则。
     *      使用 ApplicationReadyEvent 确保 Nacos 数据源（如已配置）已完成规则加载，
     *      本方法仅在 Nacos 规则基础上补充兜底规则。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initRules() {
        List<FlowRule> rules = new ArrayList<>();

        if (properties.getRules() != null && !properties.getRules().isEmpty()) {
            // 加载外部配置中定义的自定义规则
            for (SentinelProperties.FlowRuleConfig ruleConfig : properties.getRules()) {
                rules.add(buildRule(ruleConfig));
            }
            if (log.isInfoEnabled()) {
                log.info("已从外部配置加载 Sentinel 流控规则，共 {} 条", rules.size());
            }
        } else {
            // 外部配置为空，使用内置默认规则作为兜底
            rules.addAll(buildDefaultRules());
            if (log.isInfoEnabled()) {
                log.info("已加载 Sentinel 默认兜底流控规则，共 {} 条", rules.size());
            }
        }

        // 向 FlowRuleManager 注册规则；若 Nacos 已加载同名资源规则，Sentinel 将以合并方式处理
        FlowRuleManager.loadRules(rules);
    }

    /**
     * 描述: 根据外部配置项构建单条流控规则。
     *
     * @param config 外部规则配置项
     * @return 构建完成的 FlowRule
     */
    private FlowRule buildRule(final SentinelProperties.FlowRuleConfig config) {
        FlowRule rule = new FlowRule();
        rule.setResource(config.getResource());
        rule.setGrade(config.getGrade());
        rule.setCount(config.getCount());
        rule.setControlBehavior(config.getControlBehavior());
        rule.setWarmUpPeriodSec(config.getWarmUpPeriodSec());
        rule.setMaxQueueingTimeMs(config.getMaxQueueingTimeMs());
        return rule;
    }

    /**
     * 描述: 构建内置默认限流规则列表，覆盖高风险接口。
     *
     * @return 默认流控规则列表
     */
    private List<FlowRule> buildDefaultRules() {
        List<FlowRule> rules = new ArrayList<>();

        // 登录接口限流：防止暴力破解
        rules.add(createRule("POST:/api/v1/auth/login",
                RuleConstant.FLOW_GRADE_QPS, LOGIN_QPS));

        // 短信发送限流：防止短信轰炸
        rules.add(createRule("POST:/api/v1/auth/sms/send",
                RuleConstant.FLOW_GRADE_QPS, SMS_QPS));

        // 注册接口限流：防止批量注册
        rules.add(createRule("POST:/api/v1/auth/register",
                RuleConstant.FLOW_GRADE_QPS, REGISTER_QPS));

        // 通用 API 兜底限流
        rules.add(createRule("/api/v1/**",
                RuleConstant.FLOW_GRADE_QPS, CATCH_ALL_QPS));

        return rules;
    }

    /**
     * 描述: 创建简单的 QPS 流控规则（默认快速失败控制行为）。
     *
     * @param resource 受保护的资源名称
     * @param grade    限流类型（QPS 或并发线程数）
     * @param count    限流阈值
     * @return 构建完成的 FlowRule
     */
    private FlowRule createRule(final String resource, final int grade, final double count) {
        FlowRule rule = new FlowRule();
        rule.setResource(resource);
        rule.setGrade(grade);
        rule.setCount(count);
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        return rule;
    }
}
