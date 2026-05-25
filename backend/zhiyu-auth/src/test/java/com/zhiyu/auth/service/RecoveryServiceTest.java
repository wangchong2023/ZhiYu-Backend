package com.zhiyu.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.auth.dto.RecoveryApplyRequest;
import com.zhiyu.auth.dto.RecoveryApplyResponse;
import com.zhiyu.auth.entity.AccountRecoveryTicket;
import com.zhiyu.auth.mapper.AccountRecoveryTicketMapper;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.auth.password.PasswordService;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecoveryServiceTest {

    @Mock
    private AccountRecoveryTicketMapper ticketMapper;

    @Mock
    private AuthUserMapper authUserMapper;

    @Mock
    private PasswordService passwordService;

    @InjectMocks
    private RecoveryService recoveryService;

    // ── apply() ──────────────────────────────────────────────

    @Test
    void shouldCreateTicketWhenEmailMatches() {
        RecoveryApplyRequest req = new RecoveryApplyRequest();
        req.setEmail("user@example.com");
        req.setReason("Lost device");

        AuthUser user = new AuthUser();
        user.setAuthUserId(1L);
        user.setAuthUserMail("user@example.com");
        when(authUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        RecoveryApplyResponse resp = recoveryService.apply(req);

        assertThat(resp.getTicketNo()).startsWith("AR");
        assertThat(resp.getExpiresAt()).isAfter(LocalDateTime.now());
        verify(ticketMapper).insert(any(AccountRecoveryTicket.class));
    }

    @Test
    void shouldCreateTicketWhenPhoneMatches() {
        RecoveryApplyRequest req = new RecoveryApplyRequest();
        req.setPhone("13800138000");
        req.setReason("Lost device");

        AuthUser user = new AuthUser();
        user.setAuthUserId(1L);
        when(authUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        RecoveryApplyResponse resp = recoveryService.apply(req);

        assertThat(resp.getTicketNo()).startsWith("AR");
        verify(ticketMapper).insert(any(AccountRecoveryTicket.class));
    }

    @Test
    void shouldThrowValidationFailedWhenNoContactInfo() {
        RecoveryApplyRequest req = new RecoveryApplyRequest();
        req.setReason("Lost device");

        assertThatThrownBy(() -> recoveryService.apply(req))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.VALIDATION_FAILED.getCode());
    }

    @Test
    void shouldThrowNotFoundWhenNoUserMatches() {
        RecoveryApplyRequest req = new RecoveryApplyRequest();
        req.setEmail("unknown@example.com");
        req.setReason("Lost device");
        when(authUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> recoveryService.apply(req))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.RESOURCE_NOT_FOUND.getCode());
    }

    // ── review() ─────────────────────────────────────────────

    @Test
    void shouldApproveTicketAndGenerateToken() {
        AccountRecoveryTicket ticket = AccountRecoveryTicket.builder()
                .id(1L).ticketNo("AR20260523000001").userId(1L)
                .status("PENDING").expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        when(ticketMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ticket);

        recoveryService.review("AR20260523000001", true, null, 100L);

        assertThat(ticket.getStatus()).isEqualTo("APPROVED");
        assertThat(ticket.getRecoveryToken()).isNotNull();
        verify(ticketMapper).updateById(ticket);
    }

    @Test
    void shouldRejectTicket() {
        AccountRecoveryTicket ticket = AccountRecoveryTicket.builder()
                .id(1L).ticketNo("AR20260523000001").userId(1L)
                .status("PENDING").expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        when(ticketMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ticket);

        recoveryService.review("AR20260523000001", false, "Not enough info", 100L);

        assertThat(ticket.getStatus()).isEqualTo("REJECTED");
        verify(ticketMapper).updateById(ticket);
    }

    @Test
    void shouldThrowNotFoundWhenTicketNotExist() {
        when(ticketMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> recoveryService.review("NONEXIST", true, null, 100L))
                .isInstanceOf(BizException.class);
    }

    // ── resetPassword() ──────────────────────────────────────

    @Test
    void shouldResetPasswordWithValidToken() {
        AccountRecoveryTicket ticket = AccountRecoveryTicket.builder()
                .id(1L).ticketNo("AR20260523000001").userId(1L)
                .status("APPROVED").recoveryToken("valid-token")
                .recoveryTokenExpires(LocalDateTime.now().plusHours(1))
                .build();
        AuthUser user = new AuthUser();
        user.setAuthUserId(1L);
        when(ticketMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ticket);
        when(authUserMapper.selectById(1L)).thenReturn(user);
        when(passwordService.hash(anyString())).thenReturn("hashed");

        recoveryService.resetPassword("valid-token", "NewAbc12345");

        assertThat(user.getAuthUserPassword()).isEqualTo("hashed");
        verify(authUserMapper).updateById(user);
    }

    @Test
    void shouldThrowInvalidTokenWhenTokenExpired() {
        AccountRecoveryTicket ticket = AccountRecoveryTicket.builder()
                .id(1L).ticketNo("AR20260523000001").userId(1L)
                .status("APPROVED").recoveryToken("expired-token")
                .recoveryTokenExpires(LocalDateTime.now().minusHours(1))
                .build();
        when(ticketMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ticket);

        assertThatThrownBy(() -> recoveryService.resetPassword("expired-token", "NewAbc12345"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.RECOVERY_EXPIRED.getCode());
    }
}
