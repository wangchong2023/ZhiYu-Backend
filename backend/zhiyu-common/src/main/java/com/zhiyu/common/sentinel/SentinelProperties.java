package com.zhiyu.common.sentinel;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for Sentinel rate limiting.
 *
 * <p>Prefixed with {@code zhiyu.sentinel}. Rules defined here are loaded
 * programmatically at startup and used as defaults when Nacos datasource
 * is unavailable.</p>
 *
 * <p>Example YAML:</p>
 * <pre>{@code
 * zhiyu:
 *   sentinel:
 *     enabled: true
 *     rules:
 *       - resource: "POST:/api/v1/auth/login"
 *         grade: 1
 *         count: 10
 *       - resource: "POST:/api/v1/auth/sms/send"
 *         grade: 1
 *         count: 1
 *       - resource: "/api/v1/**"
 *         grade: 1
 *         count: 100
 * }</pre>
 */
@Data
@ConfigurationProperties(prefix = "zhiyu.sentinel")
public class SentinelProperties {

    /** Whether Sentinel rate limiting is enabled. */
    private boolean enabled = true;

    /** Flow control rules loaded at startup. */
    private List<FlowRuleConfig> rules = new ArrayList<>();

    /**
     * Configuration for a single Sentinel flow control rule.
     */
    @Data
    public static class FlowRuleConfig {

        /** Resource name (URL pattern or @SentinelResource value). */
        private String resource;

        /**
         * Grade type: 1 = QPS (default), 0 = thread count.
         * @see com.alibaba.csp.sentinel.slots.block.RuleConstant#FLOW_GRADE_QPS
         */
        private int grade = 1;

        /** Threshold value (QPS or thread count). */
        private double count;

        /**
         * Control behavior:
         * <ul>
         *   <li>0 = REJECT (default) — reject immediately</li>
         *   <li>1 = WARM_UP — gradually increase threshold</li>
         *   <li>2 = RATE_LIMITER — uniform queue with maxQueueingTimeMs</li>
         * </ul>
         */
        private int controlBehavior = 0;

        private static final int DEFAULT_WARM_UP_SEC = 10;
        private static final int DEFAULT_MAX_QUEUEING_MS = 500;

        /** Warm-up period in seconds (only applies when controlBehavior = WARM_UP). */
        private int warmUpPeriodSec = DEFAULT_WARM_UP_SEC;

        /** Max queueing time in ms (only applies when controlBehavior = RATE_LIMITER). */
        private int maxQueueingTimeMs = DEFAULT_MAX_QUEUEING_MS;
    }
}
