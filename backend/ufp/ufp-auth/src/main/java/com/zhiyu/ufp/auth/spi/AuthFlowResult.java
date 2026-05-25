package com.zhiyu.ufp.auth.spi;

import com.zhiyu.ufp.auth.entity.AuthUser;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthFlowResult {

    private final AuthUser user;
    private final String scope;
    private final String logType;
    private final String logAction;

    @Builder.Default
    private final boolean newUser = false;

    @Builder.Default
    private final boolean totpPending = false;
}
