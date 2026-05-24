package com.zhiyu.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.ufp.auth.entity.AuthUserIdentity;
import com.zhiyu.ufp.auth.mapper.AuthUserIdentityMapper;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {

    @Mock
    private AuthUserIdentityMapper identityMapper;

    @InjectMocks
    private IdentityService identityService;

    @Test
    void shouldListEnabledIdentities() {
        AuthUserIdentity id1 = new AuthUserIdentity();
        id1.setAuthUserIdentityId(1L);
        id1.setAuthUserId(1001L);
        id1.setProvider("WECHAT");
        id1.setEnabled(1);

        AuthUserIdentity id2 = new AuthUserIdentity();
        id2.setAuthUserIdentityId(2L);
        id2.setAuthUserId(1001L);
        id2.setProvider("GOOGLE");
        id2.setEnabled(1);

        when(identityMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(id1, id2));

        List<AuthUserIdentity> result = identityService.listIdentities(1001L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getProvider()).isEqualTo("WECHAT");
    }

    @Test
    void shouldUnbindIdentityWhenMultipleExist() {
        AuthUserIdentity identity = new AuthUserIdentity();
        identity.setAuthUserIdentityId(1L);
        identity.setAuthUserId(1001L);
        identity.setProvider("GOOGLE");
        identity.setEnabled(1);
        when(identityMapper.selectById(1L)).thenReturn(identity);
        when(identityMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        identityService.unbindIdentity(1001L, 1L);

        verify(identityMapper).updateById(identity);
        assertThat(identity.getEnabled()).isEqualTo(0);
    }

    @Test
    void shouldThrowNotFoundWhenIdentityNotBelongsToUser() {
        AuthUserIdentity identity = new AuthUserIdentity();
        identity.setAuthUserIdentityId(1L);
        identity.setAuthUserId(2002L);
        when(identityMapper.selectById(1L)).thenReturn(identity);

        assertThatThrownBy(() -> identityService.unbindIdentity(1001L, 1L))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(BizErrorCode.RESOURCE_NOT_FOUND.getCode());
    }

    @Test
    void shouldThrowCannotUnbindLastWhenOnlyOneIdentity() {
        AuthUserIdentity identity = new AuthUserIdentity();
        identity.setAuthUserIdentityId(1L);
        identity.setAuthUserId(1001L);
        when(identityMapper.selectById(1L)).thenReturn(identity);
        when(identityMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> identityService.unbindIdentity(1001L, 1L))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(BizErrorCode.CANNOT_UNBIND_LAST.getCode());

        verify(identityMapper, never()).updateById(any(AuthUserIdentity.class));
    }
}
