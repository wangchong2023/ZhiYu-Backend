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
 * Initializes Sentinel flow control rules at application startup.
 *
 * <p>Rules are sourced from {@link SentinelProperties} when configured,
 * falling back to a sensible set of built-in defaults:
 * <ul>
 *   <li>Login endpoint — 10 QPS per second</li>
 *   <li>SMS send — 1 QPS per second</li>
 *   <li>Register — 5 QPS per second</li>
 *   <li>Catch-all API — 100 QPS per second</li>
 * </ul>
 *
 * <p>These rules serve as the last-known-good fallback when Nacos
 * datasource rules are unavailable. Nacos-pushed rules take precedence
 * when the datasource is configured.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class SentinelRulesInitializer {

    private final SentinelProperties properties;

    /**
     * Load flow rules after the application context is fully ready.
     * This ensures Nacos datasource has already loaded its rules
     * (if configured), so our defaults only fill in gaps.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initRules() {
        List<FlowRule> rules = new ArrayList<>();

        if (properties.getRules() != null && !properties.getRules().isEmpty()) {
            for (SentinelProperties.FlowRuleConfig ruleConfig : properties.getRules()) {
                rules.add(buildRule(ruleConfig));
            }
            if (log.isInfoEnabled()) {
                log.info("Sentinel flow rules loaded from configuration: {} rules", rules.size());
            }
        } else {
            rules.addAll(buildDefaultRules());
            if (log.isInfoEnabled()) {
                log.info("Sentinel default flow rules loaded: {} rules", rules.size());
            }
        }

        // Load rules — Sentinel merges with Nacos-loaded rules.
        FlowRuleManager.loadRules(rules);
    }

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

    private static final double LOGIN_QPS = 10;
    private static final double SMS_QPS = 1;
    private static final double REGISTER_QPS = 5;
    private static final double CATCH_ALL_QPS = 100;

    private List<FlowRule> buildDefaultRules() {
        List<FlowRule> rules = new ArrayList<>();

        rules.add(createRule("POST:/api/v1/auth/login",
                RuleConstant.FLOW_GRADE_QPS, LOGIN_QPS));

        rules.add(createRule("POST:/api/v1/auth/sms/send",
                RuleConstant.FLOW_GRADE_QPS, SMS_QPS));

        rules.add(createRule("POST:/api/v1/auth/register",
                RuleConstant.FLOW_GRADE_QPS, REGISTER_QPS));

        rules.add(createRule("/api/v1/**",
                RuleConstant.FLOW_GRADE_QPS, CATCH_ALL_QPS));

        return rules;
    }

    private FlowRule createRule(final String resource, final int grade, final double count) {
        FlowRule rule = new FlowRule();
        rule.setResource(resource);
        rule.setGrade(grade);
        rule.setCount(count);
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        return rule;
    }
}
