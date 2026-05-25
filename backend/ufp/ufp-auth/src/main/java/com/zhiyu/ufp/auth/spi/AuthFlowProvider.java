package com.zhiyu.ufp.auth.spi;

import com.zhiyu.ufp.auth.enums.AuthGrantType;

public interface AuthFlowProvider {

    AuthGrantType supportedGrantType();

    AuthFlowResult authenticate(AuthFlowContext context);
}
