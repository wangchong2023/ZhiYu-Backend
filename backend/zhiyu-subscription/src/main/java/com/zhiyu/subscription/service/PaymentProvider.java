package com.zhiyu.subscription.service;

import java.util.Map;

public interface PaymentProvider {

    boolean verify(Map<String, String> params);

    String getChannel();
}
