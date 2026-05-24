package com.zhiyu.subscription.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class MockAlipayProvider implements PaymentProvider {

    @Override
    public boolean verify(final Map<String, String> params) {
        log.info("MockAlipayProvider verifying payment: {}", params);
        return true;
    }

    @Override
    public String getChannel() {
        return "ALIPAY";
    }
}
