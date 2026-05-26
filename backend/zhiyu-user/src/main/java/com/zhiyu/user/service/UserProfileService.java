/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: UserProfileService.java
 * 创建时间: 2026-05-27
 * 描述: 用户资料业务处理逻辑类，负责用户基本信息转换、头像静态资源存储及多数据源下的登录日志分页检索。
 */
package com.zhiyu.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.common.service.GenericService;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.entity.AuthUserLog;
import com.zhiyu.ufp.auth.service.AuthUserLogService;
import com.zhiyu.ufp.auth.service.IAuthUserService;
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

/**
 * 类名: UserProfileService
 * 描述: 用户基本资料业务处理类。注入统一平台级用户管理服务，并跨模块进行资料组装与头像逻辑控制。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    // 软删除状态标记：1表示已注销/删除
    private static final int DELETED_FLAG = 1;
    // 账户验证标记：1表示已验证
    private static final int VERIFIED_FLAG = 1;
    // 账户禁用状态：0表示禁用
    private static final int DISABLED_FLAG = 0;
    // UUID的前缀截取长度
    private static final int UUID_PREFIX_LEN = 8;
    // 头像默认存储相对目录前缀
    private static final String AVATAR_DIR_PREFIX = "avatars/";
    // 获取头像失败时退回的默认文件名
    private static final String DEFAULT_AVATAR_FILENAME = "avatar.png";
    // 默认图片后缀格式
    private static final String DEFAULT_IMAGE_EXT = "png";
    // 支持上传的用户头像文件 MIME 类型集合
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp");
    // 限制单次上传头像的最大尺寸为 2MB
    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024;

    private final IAuthUserService authUserService;
    private final AuthUserLogService authUserLogService;

    // 头像存储的本地绝对物理路径，支持通过配置注入，默认为用户主目录下的 zhiyu/avatars 文件夹
    @Value("${zhiyu.avatar.dir:${user.home}/zhiyu/avatars}")
    private String avatarDir;

    /**
     * 描述: 根据用户 ID 获取其基本个人资料
     * @param userId 用户 ID
     * @return 包含邮箱、手机号等结构的数据传输对象 UserProfileResp
     * @throws BizException 若用户不存在则抛出 RESOURCE_NOT_FOUND 异常
     */
    public UserProfileResp getProfile(final Long userId) {
        // 调用底层的核心用户认证微服务获取数据实体
        AuthUser user = authUserService.selectById(userId);
        if (user == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }
        return toResp(user);
    }

    /**
     * 描述: 修改用户昵称或更新头像相对路径，涉及写操作，加入事务管理
     * @param userId 用户 ID
     * @param request 包含昵称、头像相对路径等更新属性的请求载体
     * @return 更新后的完整资料数据传输对象 UserProfileResp
     * @throws BizException 若用户不存在则抛出 RESOURCE_NOT_FOUND 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public UserProfileResp updateProfile(final Long userId, final UpdateProfileReq request) {
        // 查询目标用户是否存在
        AuthUser user = authUserService.selectById(userId);
        if (user == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }

        // 按需设置各字段新值
        if (request.getNick() != null) {
            user.setAuthUserNick(request.getNick());
        }
        if (request.getAvatar() != null) {
            user.setAuthUserAvatar(request.getAvatar());
        }
        user.setUpdatedTime(LocalDateTime.now());
        
        // 跨模块调用服务持久化
        authUserService.updateById(user);

        return toResp(user);
    }

    /**
     * 描述: 上传头像并存储至物理磁盘，同时更新用户实体中保存的文件路径
     * @param userId 用户 ID
     * @param file 待上传的文件多部分数据流 MultipartFile
     * @return 新生成的头像相对访问路径，如 avatars/xxx.png
     * @throws BizException 包含文件尺寸超标、文件格式不支持、用户不存在、IO读写异常等情况
     */
    @Transactional(rollbackFor = Exception.class)
    public String uploadAvatar(final Long userId, final MultipartFile file) {
        // 校验文件是否为空或超过 2MB
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(BizErrorCode.FILE_SIZE_EXCEEDED);
        }
        
        // 校验文件格式是否属于合法 MIME 范围
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BizException(BizErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        // 获取当前用户并确保用户存在
        AuthUser user = authUserService.selectById(userId);
        if (user == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }

        try {
            // 解析物理路径并自动创建不存在的父目录
            Path dir = Paths.get(avatarDir);
            Files.createDirectories(dir);

            // 提取文件扩展名并转换 jpeg 后缀为 jpg
            String ext = contentType.substring(contentType.indexOf('/') + 1);
            if ("jpeg".equals(ext)) {
                ext = "jpg";
            }
            
            // 构造随机性文件名防止重名冲突
            String filename = userId + "_" + UUID.randomUUID().toString().substring(0, UUID_PREFIX_LEN)
                    + "." + ext;
            Path target = dir.resolve(filename);
            
            // 将上传的文件流复制到对应磁盘地址中
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            // 更新用户头像相对路径并持久化
            String avatarPath = AVATAR_DIR_PREFIX + filename;
            user.setAuthUserAvatar(avatarPath);
            user.setUpdatedTime(LocalDateTime.now());
            authUserService.updateById(user);

            log.info("Avatar uploaded for userId={}: {}", userId, avatarPath);
            return avatarPath;
        } catch (IOException e) {
            log.error("Avatar upload failed for userId={}", userId, e);
            throw new BizException(BizErrorCode.INTERNAL_ERROR, e);
        }
    }

    /**
     * 描述: 获取头像的物理静态文件资源，转换成对应媒体类型后返回给表现层
     * @param userId 用户 ID
     * @return 包含二进制文件流和特定 Content-Type 的 ResponseEntity
     */
    public ResponseEntity<Resource> getAvatar(final Long userId) {
        AuthUser user = authUserService.selectById(userId);
        if (user == null || user.getAuthUserAvatar() == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            // 获取相对路径的文件名，防目录穿透
            Path file = Paths.get(avatarDir).resolve(
                    Paths.get(user.getAuthUserAvatar()).getFileName());
            Resource resource = new UrlResource(file.toUri());
            
            // 如果文件在物理磁盘存在且可读，进行响应组装
            if (resource.exists() && resource.isReadable()) {
                Path fileName = file.getFileName();
                String filename = fileName != null ? fileName.toString() : DEFAULT_AVATAR_FILENAME;
                String ext = filename.contains(".")
                        ? filename.substring(filename.lastIndexOf('.') + 1) : DEFAULT_IMAGE_EXT;
                
                // 选择合适的响应媒体 Content-Type
                MediaType mediaType = switch (ext) {
                    case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
                    case "gif" -> MediaType.IMAGE_GIF;
                    default -> MediaType.IMAGE_PNG;
                };
                return ResponseEntity.ok().contentType(mediaType).body(resource);
            }
        } catch (MalformedURLException ignored) {
            // 异常兜底，默认返回 404
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * 描述: 用户自助账号注销（进入冷却期）。对用户信息作软删除及禁用处理，涉及写操作
     * @param userId 用户 ID
     * @throws BizException 若用户不存在或账号此前已触发了注销流程
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(final Long userId) {
        AuthUser user = authUserService.selectById(userId);
        if (user == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }

        // 校验是否正处于注销处理中
        if (user.getAuthUserDeleted() != null && user.getAuthUserDeleted() == DELETED_FLAG) {
            throw new BizException(BizErrorCode.DELETION_ALREADY_REQUESTED);
        }

        // 触发软删除逻辑并将启用状态置为 0
        user.setAuthUserDeleted(DELETED_FLAG);
        user.setAuthUserEnable(DISABLED_FLAG);
        user.setUpdatedTime(LocalDateTime.now());
        authUserService.updateById(user);

        log.info("Account deletion requested for userId={}", userId);
    }

    /**
     * 描述: 分页获取用户的历史登录行为日志
     * @param userId 用户 ID
     * @param page 分页页码
     * @param size 分页每页行数
     * @return 分页后包装了 LoginHistoryDto 的 Page 数据体
     */
    public Page<LoginHistoryDto> getLoginHistory(final Long userId, final int page, final int size) {
        // 使用动态多数据源，从核心认证数据库中查询登录日志
        var wrapper = new LambdaQueryWrapper<AuthUserLog>()
                .eq(AuthUserLog::getAuthUserLogUserId, userId)
                .orderByDesc(AuthUserLog::getCreatedTime);

        Page<AuthUserLog> entityPage = authUserLogService.selectPage(
                new Page<>(page, size), wrapper);
                
        // 进行对象转换映射
        return GenericService.pageDto(entityPage, e -> LoginHistoryDto.builder()
                .id(e.getAuthUserLogId())
                .username(e.getAuthUserLogUserDisplay())
                .action(e.getAuthUserLogAction())
                .type(e.getAuthUserLogType())
                .result(e.getAuthUserLogResult())
                .ip(e.getAuthUserLogIp())
                .device(e.getAuthUserLogDevice())
                .location(e.getAuthUserLogLocation())
                .time(e.getCreatedTime())
                .build());
    }

    /**
     * 描述: 将底层实体类转换成暴露给上层表现层使用的脱敏数据传输对象
     * @param user 用户核心实体 AuthUser
     * @return 脱敏后的资料数据 DTO
     */
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

