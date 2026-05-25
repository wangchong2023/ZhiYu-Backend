package com.zhiyu.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.auth.dto.RecoveryApplyRequest;
import com.zhiyu.auth.dto.RecoveryApplyResponse;
import com.zhiyu.auth.entity.AccountRecoveryTicket;
import com.zhiyu.auth.mapper.AccountRecoveryTicketMapper;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.auth.password.PasswordService;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecoveryService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final int RECOVERY_TOKEN_HOURS = 24;
    private static final int TICKET_EXPIRE_DAYS = 7;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AccountRecoveryTicketMapper ticketMapper;
    private final AuthUserMapper authUserMapper;
    private final PasswordService passwordService;

    @Transactional(rollbackFor = Exception.class)
    public RecoveryApplyResponse apply(final RecoveryApplyRequest request) {
        if (!StringUtils.hasText(request.getEmail())
                && !StringUtils.hasText(request.getPhone())
                && !StringUtils.hasText(request.getUsername())) {
            throw new BizException(BizErrorCode.VALIDATION_FAILED);
        }

        AuthUser matchedUser = null;
        if (StringUtils.hasText(request.getEmail())) {
            matchedUser = authUserMapper.selectOne(new LambdaQueryWrapper<AuthUser>()
                    .eq(AuthUser::getAuthUserMail, request.getEmail()));
        }
        if (matchedUser == null && StringUtils.hasText(request.getPhone())) {
            matchedUser = authUserMapper.selectOne(new LambdaQueryWrapper<AuthUser>()
                    .eq(AuthUser::getAuthUserMobile, request.getPhone()));
        }
        if (matchedUser == null && StringUtils.hasText(request.getUsername())) {
            matchedUser = authUserMapper.selectOne(new LambdaQueryWrapper<AuthUser>()
                    .eq(AuthUser::getAuthUserUsername, request.getUsername()));
        }

        if (matchedUser == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }

        Map<String, String> info = new LinkedHashMap<>();
        if (StringUtils.hasText(request.getEmail())) { info.put("email", request.getEmail()); }
        if (StringUtils.hasText(request.getPhone())) { info.put("phone", request.getPhone()); }
        if (StringUtils.hasText(request.getUsername())) { info.put("username", request.getUsername()); }
        info.put("reason", request.getReason());

        String ticketNo = "AR" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + String.format("%06d", System.currentTimeMillis() % 1000000);
        LocalDateTime now = LocalDateTime.now();

        AccountRecoveryTicket ticket = AccountRecoveryTicket.builder()
                .ticketNo(ticketNo)
                .userId(matchedUser.getAuthUserId())
                .submittedInfo(toJson(info))
                .status(STATUS_PENDING)
                .appliedAt(now)
                .expiresAt(now.plusDays(TICKET_EXPIRE_DAYS))
                .build();
        ticketMapper.insert(ticket);

        if (log.isInfoEnabled()) {
            log.info("Recovery ticket created: ticketNo={}, userId={}", ticketNo, matchedUser.getAuthUserId());
        }
        return RecoveryApplyResponse.builder()
                .ticketNo(ticketNo)
                .expiresAt(ticket.getExpiresAt())
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public void review(final String ticketNo, final boolean approve,
                        final String note, final Long reviewerId) {
        AccountRecoveryTicket ticket = ticketMapper.selectOne(
                new LambdaQueryWrapper<AccountRecoveryTicket>()
                        .eq(AccountRecoveryTicket::getTicketNo, ticketNo));
        if (ticket == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!STATUS_PENDING.equals(ticket.getStatus())) {
            throw new BizException(BizErrorCode.ORDER_ALREADY_PAID);
        }

        LocalDateTime now = LocalDateTime.now();
        ticket.setReviewerId(reviewerId);
        ticket.setReviewNote(note);
        ticket.setReviewedAt(now);

        if (approve) {
            ticket.setStatus(STATUS_APPROVED);
            String token = UUID.randomUUID().toString().replace("-", "");
            ticket.setRecoveryToken(token);
            ticket.setRecoveryTokenExpires(now.plusHours(RECOVERY_TOKEN_HOURS));
            log.info("Recovery ticket approved: ticketNo={}, reviewerId={}, token=***", ticketNo, reviewerId);
        } else {
            ticket.setStatus(STATUS_REJECTED);
            log.info("Recovery ticket rejected: ticketNo={}, reviewerId={}", ticketNo, reviewerId);
        }
        ticketMapper.updateById(ticket);
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(final String recoveryToken, final String newPassword) {
        AccountRecoveryTicket ticket = ticketMapper.selectOne(
                new LambdaQueryWrapper<AccountRecoveryTicket>()
                        .eq(AccountRecoveryTicket::getRecoveryToken, recoveryToken));
        if (ticket == null || !STATUS_APPROVED.equals(ticket.getStatus())) {
            throw new BizException(BizErrorCode.RESET_LINK_INVALID);
        }
        if (ticket.getRecoveryTokenExpires() != null
                && ticket.getRecoveryTokenExpires().isBefore(LocalDateTime.now())) {
            throw new BizException(BizErrorCode.RECOVERY_EXPIRED);
        }

        AuthUser user = authUserMapper.selectById(ticket.getUserId());
        if (user == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }

        user.setAuthUserPassword(passwordService.hash(newPassword));
        authUserMapper.updateById(user);

        ticket.setRecoveryToken(null);
        ticket.setRecoveryTokenExpires(null);
        ticketMapper.updateById(ticket);

        if (log.isInfoEnabled()) {
            log.info("Password reset via recovery: userId={}, ticketNo={}", user.getAuthUserId(), ticket.getTicketNo());
        }
    }

    private String toJson(final Object obj) {
        try {
            return JSON.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
