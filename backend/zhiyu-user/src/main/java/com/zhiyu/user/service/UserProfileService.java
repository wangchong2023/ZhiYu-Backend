package com.zhiyu.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.entity.AuthUserLog;
import com.zhiyu.ufp.auth.mapper.AuthUserLogMapper;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import com.zhiyu.user.dto.LoginHistoryDto;
import com.zhiyu.user.dto.UpdateProfileReq;
import com.zhiyu.user.dto.UserProfileResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private static final int DELETED_FLAG = 1;
    private static final int VERIFIED_FLAG = 1;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp");
    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024;

    private final AuthUserMapper authUserMapper;
    private final AuthUserLogMapper authUserLogMapper;

    @Value("${zhiyu.avatar.dir:${user.home}/zhiyu/avatars}")
    private String avatarDir;

    public UserProfileResp getProfile(final Long userId) {
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }
        return toResp(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserProfileResp updateProfile(final Long userId, final UpdateProfileReq request) {
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }

        if (request.getNick() != null) {
            user.setAuthUserNick(request.getNick());
        }
        if (request.getAvatar() != null) {
            user.setAuthUserAvatar(request.getAvatar());
        }
        user.setUpdatedTime(LocalDateTime.now());
        authUserMapper.updateById(user);

        return toResp(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public String uploadAvatar(final Long userId, final MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(BizErrorCode.FILE_SIZE_EXCEEDED);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BizException(BizErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }

        try {
            Path dir = Paths.get(avatarDir);
            Files.createDirectories(dir);

            String ext = contentType.substring(contentType.indexOf('/') + 1);
            if ("jpeg".equals(ext)) ext = "jpg";
            String filename = userId + "_" + UUID.randomUUID().toString().substring(0, 8)
                    + "." + ext;
            Path target = dir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            String avatarPath = "avatars/" + filename;
            user.setAuthUserAvatar(avatarPath);
            user.setUpdatedTime(LocalDateTime.now());
            authUserMapper.updateById(user);

            log.info("Avatar uploaded for userId={}: {}", userId, avatarPath);
            return avatarPath;
        } catch (IOException e) {
            log.error("Avatar upload failed for userId={}", userId, e);
            throw new BizException(BizErrorCode.INTERNAL_ERROR);
        }
    }

    public ResponseEntity<Resource> getAvatar(final Long userId) {
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null || user.getAuthUserAvatar() == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            Path file = Paths.get(avatarDir).resolve(
                    Paths.get(user.getAuthUserAvatar()).getFileName());
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                String filename = file.getFileName().toString();
                String ext = filename.contains(".")
                        ? filename.substring(filename.lastIndexOf('.') + 1) : "png";
                MediaType mediaType = switch (ext) {
                    case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
                    case "gif" -> MediaType.IMAGE_GIF;
                    default -> MediaType.IMAGE_PNG;
                };
                return ResponseEntity.ok().contentType(mediaType).body(resource);
            }
        } catch (MalformedURLException ignored) {
        }
        return ResponseEntity.notFound().build();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(final Long userId) {
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }

        if (user.getAuthUserDeleted() != null && user.getAuthUserDeleted() == DELETED_FLAG) {
            throw new BizException(BizErrorCode.DELETION_ALREADY_REQUESTED);
        }

        user.setAuthUserDeleted(DELETED_FLAG);
        user.setAuthUserEnable(0);
        user.setUpdatedTime(LocalDateTime.now());
        authUserMapper.updateById(user);

        log.info("Account deletion requested for userId={}", userId);
    }

    public Page<LoginHistoryDto> getLoginHistory(final Long userId, final int page, final int size) {
        var wrapper = new LambdaQueryWrapper<AuthUserLog>()
                .eq(AuthUserLog::getAuthUserLogUserId, userId)
                .orderByDesc(AuthUserLog::getCreatedTime);

        Page<AuthUserLog> entityPage = authUserLogMapper.selectPage(
                new Page<>(page, size), wrapper);
        Page<LoginHistoryDto> dtoPage = new Page<>(page, size, entityPage.getTotal());
        dtoPage.setRecords(entityPage.getRecords().stream()
                .map(e -> LoginHistoryDto.builder()
                        .id(e.getAuthUserLogId())
                        .username(e.getAuthUserLogUserDisplay())
                        .action(e.getAuthUserLogAction())
                        .type(e.getAuthUserLogType())
                        .result(e.getAuthUserLogResult())
                        .ip(e.getAuthUserLogIp())
                        .device(e.getAuthUserLogDevice())
                        .location(e.getAuthUserLogLocation())
                        .time(e.getCreatedTime())
                        .build())
                .collect(Collectors.toList()));
        return dtoPage;
    }

    private UserProfileResp toResp(final AuthUser user) {
        return UserProfileResp.builder()
                .userId(user.getAuthUserId())
                .username(user.getAuthUserUsername())
                .nick(user.getAuthUserNick())
                .avatar(user.getAuthUserAvatar())
                .email(user.getAuthUserMail())
                .emailVerified(user.getAuthUserMailVerified() != null
                        && user.getAuthUserMailVerified() == VERIFIED_FLAG)
                .mobile(user.getAuthUserMobile())
                .mobileVerified(user.getAuthUserMobileVerified() != null
                        && user.getAuthUserMobileVerified() == VERIFIED_FLAG)
                .scope(user.getAuthUserScope())
                .createdTime(user.getCreatedTime())
                .build();
    }
}
