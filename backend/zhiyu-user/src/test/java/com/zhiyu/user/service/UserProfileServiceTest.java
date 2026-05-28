package com.zhiyu.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.entity.AuthUserLog;
import com.zhiyu.ufp.auth.service.AuthUserLogService;
import com.zhiyu.ufp.auth.service.IAuthUserService;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import com.zhiyu.user.dto.LoginHistoryDto;
import com.zhiyu.user.dto.UpdateProfileReq;
import com.zhiyu.user.dto.UserProfileResp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private IAuthUserService authUserService;

    @Mock
    private AuthUserLogService authUserLogService;

    @TempDir
    Path tempDir;

    @InjectMocks
    private UserProfileService userProfileService;

    private static AuthUser buildUser(Long id, String username, String nick, String email,
                                       Integer emailVerified, String mobile, Integer mobileVerified,
                                       Integer deleted, Integer enabled) {
        return AuthUser.builder()
                .authUserId(id)
                .authUserUsername(username)
                .authUserNick(nick)
                .authUserMail(email)
                .authUserMailVerified(emailVerified)
                .authUserMobile(mobile)
                .authUserMobileVerified(mobileVerified)
                .authUserScope("openid")
                .authUserDeleted(deleted)
                .authUserEnable(enabled)
                .createdTime(LocalDateTime.of(2025, 1, 1, 0, 0))
                .build();
    }

    // ── getProfile() ─────────────────────────────────────────────

    @Test
    void shouldReturnProfileWhenUserExists() {
        AuthUser user = buildUser(1001L, "zhangsan", "张三", "zhangsan@example.com",
                1, "13800138000", 1, null, 1);
        when(authUserService.selectById(1001L)).thenReturn(user);

        UserProfileResp result = userProfileService.getProfile(1001L);

        assertThat(result.getUserId()).isEqualTo(1001L);
        assertThat(result.getUsername()).isEqualTo("zhangsan");
        assertThat(result.getNick()).isEqualTo("张三");
        assertThat(result.getEmail()).isEqualTo("zhangsan@example.com");
        assertThat(result.isEmailVerified()).isTrue();
        assertThat(result.getMobile()).isEqualTo("13800138000");
        assertThat(result.isMobileVerified()).isTrue();
        assertThat(result.getScope()).isEqualTo("openid");
        assertThat(result.getCreatedTime()).isNotNull();
        verify(authUserService).selectById(1001L);
    }

    @Test
    void shouldThrowBizExceptionWhenUserNotFoundForGetProfile() {
        when(authUserService.selectById(9999L)).thenReturn(null);

        assertThatThrownBy(() -> userProfileService.getProfile(9999L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.RESOURCE_NOT_FOUND.getCode());

        verify(authUserService).selectById(9999L);
    }

    @Test
    void shouldReturnProfileWithEmailNotVerifiedWhenNull() {
        AuthUser user = buildUser(1001L, "zhangsan", "张三", "zhangsan@example.com",
                null, null, null, null, 1);
        when(authUserService.selectById(1001L)).thenReturn(user);

        UserProfileResp result = userProfileService.getProfile(1001L);

        assertThat(result.isEmailVerified()).isFalse();
        assertThat(result.isMobileVerified()).isFalse();
    }

    @Test
    void shouldReturnProfileWithEmailNotVerifiedWhenZero() {
        AuthUser user = buildUser(1001L, "zhangsan", "张三", "zhangsan@example.com",
                0, null, 0, null, 1);
        when(authUserService.selectById(1001L)).thenReturn(user);

        UserProfileResp result = userProfileService.getProfile(1001L);

        assertThat(result.isEmailVerified()).isFalse();
        assertThat(result.isMobileVerified()).isFalse();
    }

    // ── updateProfile() ──────────────────────────────────────────

    @Test
    void shouldUpdateProfileWhenUserExists() {
        AuthUser user = buildUser(1001L, "zhangsan", "旧昵称", "zhangsan@example.com",
                1, null, null, null, 1);
        when(authUserService.selectById(1001L)).thenReturn(user);

        UpdateProfileReq request = new UpdateProfileReq();
        request.setNick("新昵称");

        UserProfileResp result = userProfileService.updateProfile(1001L, request);

        assertThat(result.getNick()).isEqualTo("新昵称");
        verify(authUserService).updateById(user);
    }

    @Test
    void shouldThrowBizExceptionWhenUpdateUserNotFound() {
        when(authUserService.selectById(9999L)).thenReturn(null);

        UpdateProfileReq request = new UpdateProfileReq();
        request.setNick("新昵称");

        assertThatThrownBy(() -> userProfileService.updateProfile(9999L, request))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.RESOURCE_NOT_FOUND.getCode());

        verify(authUserService, never()).updateById(any(AuthUser.class));
    }

    @Test
    void shouldNotUpdateNickWhenNickIsNull() {
        AuthUser user = buildUser(1001L, "zhangsan", "旧昵称", "zhangsan@example.com",
                1, null, null, null, 1);
        when(authUserService.selectById(1001L)).thenReturn(user);

        UpdateProfileReq request = new UpdateProfileReq();
        request.setNick(null);

        UserProfileResp result = userProfileService.updateProfile(1001L, request);

        assertThat(result.getNick()).isEqualTo("旧昵称");
        verify(authUserService).updateById(user);
    }

    @Test
    void shouldSetUpdatedTimeWhenUpdatingProfile() {
        AuthUser user = buildUser(1001L, "zhangsan", "旧昵称", "zhangsan@example.com",
                1, null, null, null, 1);
        when(authUserService.selectById(1001L)).thenReturn(user);

        UpdateProfileReq request = new UpdateProfileReq();
        request.setNick("新昵称");

        userProfileService.updateProfile(1001L, request);

        assertThat(user.getUpdatedTime()).isNotNull();
    }

    @Test
    void shouldUpdateProfileWithoutNickChange() {
        AuthUser user = buildUser(1001L, "zhangsan", "不变昵称", "zhangsan@example.com",
                1, null, null, null, 1);
        when(authUserService.selectById(1001L)).thenReturn(user);

        UpdateProfileReq request = new UpdateProfileReq();
        // No nick set (null) — should keep existing nick

        UserProfileResp result = userProfileService.updateProfile(1001L, request);

        assertThat(result.getNick()).isEqualTo("不变昵称");
        verify(authUserService).updateById(user);
    }

    // ── deleteAccount() ───────────────────────────────────────────

    @Test
    void shouldDeleteAccountSuccessfully() {
        AuthUser user = buildUser(1001L, "zhangsan", "张三", "zhangsan@example.com",
                1, null, null, null, 1);
        when(authUserService.selectById(1001L)).thenReturn(user);

        userProfileService.deleteAccount(1001L);

        assertThat(user.getAuthUserDeleted()).isEqualTo(1);
        assertThat(user.getAuthUserEnable()).isZero();
        assertThat(user.getUpdatedTime()).isNotNull();
        verify(authUserService).updateById(user);
    }

    @Test
    void shouldThrowBizExceptionWhenDeleteUserNotFound() {
        when(authUserService.selectById(9999L)).thenReturn(null);

        assertThatThrownBy(() -> userProfileService.deleteAccount(9999L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.RESOURCE_NOT_FOUND.getCode());

        verify(authUserService, never()).updateById(any(AuthUser.class));
    }

    @Test
    void shouldThrowBizExceptionWhenAlreadyDeleted() {
        AuthUser user = buildUser(1001L, "zhangsan", "张三", "zhangsan@example.com",
                1, null, null, 1, 0);
        when(authUserService.selectById(1001L)).thenReturn(user);

        assertThatThrownBy(() -> userProfileService.deleteAccount(1001L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.DELETION_ALREADY_REQUESTED.getCode());

        verify(authUserService, never()).updateById(any(AuthUser.class));
    }

    @Test
    void shouldNotThrowWhenAlreadyDeletedIsZero() {
        AuthUser user = buildUser(1001L, "zhangsan", "张三", "zhangsan@example.com",
                1, null, null, 0, 1);
        when(authUserService.selectById(1001L)).thenReturn(user);

        userProfileService.deleteAccount(1001L);

        assertThat(user.getAuthUserDeleted()).isEqualTo(1);
        verify(authUserService).updateById(user);
    }

    // ── toResp() internal conversion ─────────────────────────────

    @Test
    void shouldConvertAuthUserToUserProfileRespWithAllFields() {
        AuthUser user = buildUser(1002L, "lisi", "李四", "lisi@example.com",
                1, "13900139000", 0, null, 1);
        user.setAuthUserScope("admin");
        when(authUserService.selectById(1002L)).thenReturn(user);

        UserProfileResp result = userProfileService.getProfile(1002L);

        assertThat(result.getUserId()).isEqualTo(1002L);
        assertThat(result.getUsername()).isEqualTo("lisi");
        assertThat(result.getNick()).isEqualTo("李四");
        assertThat(result.getEmail()).isEqualTo("lisi@example.com");
        assertThat(result.isEmailVerified()).isTrue();
        assertThat(result.getMobile()).isEqualTo("13900139000");
        assertThat(result.isMobileVerified()).isFalse();
        assertThat(result.getScope()).isEqualTo("admin");
        assertThat(result.getCreatedTime()).isEqualTo(LocalDateTime.of(2025, 1, 1, 0, 0));
    }

    // ── uploadAvatar() ───────────────────────────────────────────

    @Test
    void shouldUploadAvatarSuccessfully() throws Exception {
        // 1. 设置临时测试头像存储目录
        ReflectionTestUtils.setField(userProfileService, "avatarDir", tempDir.toString());

        AuthUser user = buildUser(1001L, "zhangsan", "张三", "zhangsan@example.com",
                1, null, null, null, 1);
        when(authUserService.selectById(1001L)).thenReturn(user);

        // 2. Mock 磁盘上传的 MultipartFile
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", "png-content".getBytes());

        // 3. 执行头像上传业务
        String result = userProfileService.uploadAvatar(1001L, file);

        // 4. 校验头像保存路径是否回写成功
        assertThat(result).startsWith("avatars/1001_");
        assertThat(result).endsWith(".png");
        verify(authUserService).updateById(user);
    }

    @Test
    void shouldThrowExceptionWhenFileIsEmpty() {
        MockMultipartFile file = new MockMultipartFile("file", "", "image/png", new byte[0]);

        assertThatThrownBy(() -> userProfileService.uploadAvatar(1001L, file))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.FILE_SIZE_EXCEEDED.getCode());
    }

    @Test
    void shouldThrowExceptionWhenFileTypeNotAllowed() {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());

        assertThatThrownBy(() -> userProfileService.uploadAvatar(1001L, file))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.UNSUPPORTED_FILE_TYPE.getCode());
    }

    @Test
    void shouldThrowExceptionWhenUserNotFoundForUpload() {
        ReflectionTestUtils.setField(userProfileService, "avatarDir", tempDir.toString());
        when(authUserService.selectById(9999L)).thenReturn(null);

        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", "png-content".getBytes());

        assertThatThrownBy(() -> userProfileService.uploadAvatar(9999L, file))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.RESOURCE_NOT_FOUND.getCode());
    }

    // ── getAvatar() ──────────────────────────────────────────────

    @Test
    void shouldReturnNotFoundWhenUserHasNoAvatar() {
        when(authUserService.selectById(1001L)).thenReturn(null);
        ResponseEntity<Resource> response = userProfileService.getAvatar(1001L);
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void shouldReturnAvatarFileWhenExists() throws Exception {
        ReflectionTestUtils.setField(userProfileService, "avatarDir", tempDir.toString());
        
        // 1. 预先在临时物理目录中写入头像文件，模拟磁盘物理存在
        Path avatarFile = tempDir.resolve("1001_avatar.png");
        Files.write(avatarFile, "png-content".getBytes());

        AuthUser user = buildUser(1001L, "zhangsan", "张三", "zhangsan@example.com",
                1, null, null, null, 1);
        user.setAuthUserAvatar("avatars/1001_avatar.png");
        when(authUserService.selectById(1001L)).thenReturn(user);

        // 2. 执行获取头像流
        ResponseEntity<Resource> response = userProfileService.getAvatar(1001L);

        // 3. 校验响应媒体属性与就绪状态
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/png");
    }

    // ── getLoginHistory() ────────────────────────────────────────

    @Test
    void shouldReturnLoginHistoryPage() {
        AuthUserLog logEntity = new AuthUserLog();
        logEntity.setAuthUserLogId(101L);
        logEntity.setAuthUserLogUserId(1001L);
        logEntity.setAuthUserLogUserDisplay("zhangsan");
        logEntity.setAuthUserLogAction("login");
        logEntity.setAuthUserLogType("1");
        logEntity.setAuthUserLogResult("1");
        logEntity.setAuthUserLogIp("127.0.0.1");
        logEntity.setAuthUserLogDevice("mac");
        logEntity.setAuthUserLogLocation("CN");
        logEntity.setCreatedTime(LocalDateTime.now());

        Page<AuthUserLog> pageMock = new Page<>(1, 10);
        pageMock.setRecords(List.of(logEntity));
        pageMock.setTotal(1);

        when(authUserLogService.selectPage(any(), any())).thenReturn(pageMock);

        Page<LoginHistoryDto> result = userProfileService.getLoginHistory(1001L, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
        LoginHistoryDto dto = result.getRecords().get(0);
        assertThat(dto.getId()).isEqualTo(101L);
        assertThat(dto.getUsername()).isEqualTo("zhangsan");
        assertThat(dto.getAction()).isEqualTo("login");
    }

    /**
     * 描述: 测试当上传头像在执行磁盘流物理复制过程中发生异常（如磁盘已满抛出 IOException）时，
     *       系统能够完美拦截捕获并防御封装为 BizException(INTERNAL_ERROR)，满足 100% 异常分支覆盖。
     */
    @Test
    void shouldThrowBizExceptionWhenAvatarCopyFailsWithIOException() throws Exception {
        ReflectionTestUtils.setField(userProfileService, "avatarDir", tempDir.toString());
        
        AuthUser user = buildUser(1001L, "zhangsan", "张三", "zhangsan@example.com",
                1, null, null, null, 1);
        when(authUserService.selectById(1001L)).thenReturn(user);

        // 构造一个在 getInputStream 时抛出 IOException 的 MockMultipartFile
        MultipartFile badFile = org.mockito.Mockito.mock(MultipartFile.class);
        when(badFile.isEmpty()).thenReturn(false);
        when(badFile.getSize()).thenReturn(1000L);
        when(badFile.getContentType()).thenReturn("image/png");
        when(badFile.getInputStream()).thenThrow(new IOException("Simulated disk full IOException"));

        assertThatThrownBy(() -> userProfileService.uploadAvatar(1001L, badFile))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.INTERNAL_ERROR.getCode());
    }

    /**
     * 描述: 测试当用户账户已经处于软删除注销处理中时，再次触发销户流程将直接拦截并抛出 DELETION_ALREADY_REQUESTED 错误。
     */
    @Test
    void shouldThrowExceptionWhenAccountAlreadyDeletionRequested() {
        // 构建一个已注销状态用户 (authUserDeleted = 1)
        AuthUser user = buildUser(1001L, "zhangsan", "张三", "zhangsan@example.com",
                1, null, null, 1, 0);
        when(authUserService.selectById(1001L)).thenReturn(user);

        assertThatThrownBy(() -> userProfileService.deleteAccount(1001L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.DELETION_ALREADY_REQUESTED.getCode());
    }
}
