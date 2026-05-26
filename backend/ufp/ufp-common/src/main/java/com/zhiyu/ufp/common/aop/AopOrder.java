package com.zhiyu.ufp.common.aop;

final class AopOrder {
    static final int UFP_DS = org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 10;

    private AopOrder() {
    }
}
