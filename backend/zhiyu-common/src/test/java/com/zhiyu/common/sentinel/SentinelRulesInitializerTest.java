package com.zhiyu.common.sentinel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class SentinelRulesInitializerTest {

    @Test
    void shouldInitializeEmptyRulesWithoutException() {
        SentinelProperties props = new SentinelProperties();
        SentinelRulesInitializer initializer = new SentinelRulesInitializer(props);
        assertThatCode(initializer::initRules).doesNotThrowAnyException();
    }

    @Test
    void shouldInitializeWithCustomRulesWithoutException() {
        SentinelProperties props = new SentinelProperties();
        SentinelProperties.FlowRuleConfig rule = new SentinelProperties.FlowRuleConfig();
        rule.setResource("POST:/api/v1/auth/login");
        rule.setGrade(1);
        rule.setCount(10.0);
        props.setRules(java.util.List.of(rule));

        SentinelRulesInitializer initializer = new SentinelRulesInitializer(props);
        assertThatCode(initializer::initRules).doesNotThrowAnyException();
    }
}
