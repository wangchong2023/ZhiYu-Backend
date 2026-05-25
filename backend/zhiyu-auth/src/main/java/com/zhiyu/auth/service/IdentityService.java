package com.zhiyu.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.ufp.auth.entity.AuthUserIdentity;
import com.zhiyu.ufp.auth.mapper.AuthUserIdentityMapper;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityService {

    private static final int PROVIDER_EMAIL = 1;
    private static final int PROVIDER_PHONE = 2;

    private final AuthUserIdentityMapper identityMapper;

    public List<AuthUserIdentity> listIdentities(final Long userId) {
        return identityMapper.selectList(
                new LambdaQueryWrapper<AuthUserIdentity>()
                        .eq(AuthUserIdentity::getAuthUserId, userId)
                        .eq(AuthUserIdentity::getEnabled, 1));
    }

    @Transactional(rollbackFor = Exception.class)
    public void unbindIdentity(final Long userId, final Long identityId) {
        AuthUserIdentity identity = identityMapper.selectById(identityId);
        if (identity == null || !identity.getAuthUserId().equals(userId)) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }

        long activeCount = identityMapper.selectCount(
                new LambdaQueryWrapper<AuthUserIdentity>()
                        .eq(AuthUserIdentity::getAuthUserId, userId)
                        .eq(AuthUserIdentity::getEnabled, 1));
        if (activeCount <= PROVIDER_PHONE) {
            throw new BizException(BizErrorCode.CANNOT_UNBIND_LAST);
        }

        identity.setEnabled(0);
        identityMapper.updateById(identity);
        if (log.isInfoEnabled()) {
            log.info("Identity unbound: identityId={}, userId={}, provider={}",
                    identityId, userId, identity.getProvider());
        }
    }
}
