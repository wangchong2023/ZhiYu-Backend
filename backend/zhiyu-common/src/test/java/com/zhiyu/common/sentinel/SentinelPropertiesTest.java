package com.zhiyu.common.sentinel;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SentinelPropertiesTest {

    @Test
    void shouldHaveDefaultEnabledTrue() {
        SentinelProperties props = new SentinelProperties();
        assertThat(props.isEnabled()).isTrue();
    }

    @Test
    void shouldHaveEmptyRulesByDefault() {
        SentinelProperties props = new SentinelProperties();
        assertThat(props.getRules()).isEmpty();
    }

    @Test
    void shouldAllowCustomRules() {
        SentinelProperties.FlowRuleConfig rule = new SentinelProperties.FlowRuleConfig();
        rule.setResource("/api/v1/auth/login");
        rule.setGrade(1);
        rule.setCount(10.0);

        SentinelProperties props = new SentinelProperties();
        props.setRules(List.of(rule));

        assertThat(props.getRules()).hasSize(1);
        assertThat(props.getRules().get(0).getResource()).isEqualTo("/api/v1/auth/login");
        assertThat(props.getRules().get(0).getCount()).isEqualTo(10.0);
    }

    @Test
    void flowRuleConfigShouldHaveDefaultValues() {
        SentinelProperties.FlowRuleConfig rule = new SentinelProperties.FlowRuleConfig();
        assertThat(rule.getGrade()).isEqualTo(1);
        assertThat(rule.getControlBehavior()).isZero();
        assertThat(rule.getWarmUpPeriodSec()).isEqualTo(10);
        assertThat(rule.getMaxQueueingTimeMs()).isEqualTo(500);
    }
}
