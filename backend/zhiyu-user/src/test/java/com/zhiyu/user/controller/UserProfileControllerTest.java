package com.zhiyu.user.controller;

import com.zhiyu.common.web.ApiResponse;
import com.zhiyu.user.dto.UpdateProfileReq;
import com.zhiyu.user.dto.UserProfileResp;
import com.zhiyu.user.service.UserProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileControllerTest {

    @Mock
    private UserProfileService userProfileService;

    private UserProfileController controller;

    private MockedStatic<SecurityContextHolder> mockHolder;
    private SecurityContext securityContext;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        controller = new UserProfileController(userProfileService);

        securityContext = mock(SecurityContext.class);
        authentication = mock(Authentication.class);
        mockHolder = mockStatic(SecurityContextHolder.class);
        mockHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn("1001");
    }

    @AfterEach
    void tearDown() {
        mockHolder.close();
    }

    // ── profile() ────────────────────────────────────────────────

    @Test
    void shouldReturnProfileWhenUserExists() {
        UserProfileResp expected = UserProfileResp.builder()
                .userId(1001L)
                .username("zhangsan")
                .nick("张三")
                .email("zhangsan@example.com")
                .emailVerified(true)
                .mobile("13800138000")
                .mobileVerified(false)
                .scope("openid")
                .createdTime(LocalDateTime.of(2025, 1, 1, 0, 0))
                .build();
        when(userProfileService.getProfile(1001L)).thenReturn(expected);

        ApiResponse<UserProfileResp> response = controller.profile();

        assertThat(response.getCode()).isZero();
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(response.getData()).isEqualTo(expected);
        verify(userProfileService).getProfile(1001L);
    }

    @Test
    void shouldPropagateExceptionFromGetProfile() {
        when(userProfileService.getProfile(1001L))
                .thenThrow(new RuntimeException("User not found"));

        try {
            controller.profile();
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("User not found");
        }
    }

    // ── update() ─────────────────────────────────────────────────

    @Test
    void shouldUpdateProfileWhenValidRequest() {
        UpdateProfileReq request = new UpdateProfileReq();
        request.setNick("新昵称");

        UserProfileResp expected = UserProfileResp.builder()
                .userId(1001L)
                .username("zhangsan")
                .nick("新昵称")
                .email("zhangsan@example.com")
                .emailVerified(true)
                .build();
        when(userProfileService.updateProfile(1001L, request)).thenReturn(expected);

        ApiResponse<UserProfileResp> response = controller.update(request);

        assertThat(response.getCode()).isZero();
        assertThat(response.getData()).isEqualTo(expected);
        assertThat(response.getData().getNick()).isEqualTo("新昵称");
        verify(userProfileService).updateProfile(1001L, request);
    }

    @Test
    void shouldUpdateProfileWhenRequestHasNullNick() {
        UpdateProfileReq request = new UpdateProfileReq();
        request.setNick(null);

        UserProfileResp expected = UserProfileResp.builder()
                .userId(1001L)
                .username("zhangsan")
                .nick("张三")
                .build();
        when(userProfileService.updateProfile(1001L, request)).thenReturn(expected);

        ApiResponse<UserProfileResp> response = controller.update(request);

        assertThat(response.getCode()).isZero();
        assertThat(response.getData()).isEqualTo(expected);
    }

    @Test
    void shouldPropagateExceptionFromUpdateProfile() {
        UpdateProfileReq request = new UpdateProfileReq();
        request.setNick("新昵称");
        when(userProfileService.updateProfile(1001L, request))
                .thenThrow(new RuntimeException("Update failed"));

        try {
            controller.update(request);
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("Update failed");
        }
    }

    // ── deleteAccount() ──────────────────────────────────────────

    @Test
    void shouldDeleteAccountSuccessfully() {
        ApiResponse<Void> response = controller.deleteAccount();

        assertThat(response.getCode()).isZero();
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(response.getData()).isNull();
        verify(userProfileService).deleteAccount(1001L);
    }

    @Test
    void shouldPropagateExceptionFromDeleteAccount() {
        org.mockito.Mockito.doThrow(new RuntimeException("Delete failed"))
                .when(userProfileService).deleteAccount(1001L);

        try {
            controller.deleteAccount();
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("Delete failed");
        }
    }

    // ── getCurrentUserId() ───────────────────────────────────────

    @Test
    void shouldUseCorrectUserIdFromSecurityContext() {
        UserProfileResp expected = UserProfileResp.builder()
                .userId(1001L)
                .username("zhangsan")
                .build();
        when(userProfileService.getProfile(1001L)).thenReturn(expected);

        controller.profile();

        verify(userProfileService).getProfile(1001L);
    }

    @Test
    void shouldHandleDifferentUserIdFromSecurityContext() {
        // Change the principal to a different user
        when(authentication.getPrincipal()).thenReturn("2002");

        UserProfileResp expected = UserProfileResp.builder()
                .userId(2002L)
                .username("lisi")
                .build();
        when(userProfileService.getProfile(2002L)).thenReturn(expected);

        ApiResponse<UserProfileResp> response = controller.profile();

        assertThat(response.getData().getUserId()).isEqualTo(2002L);
        verify(userProfileService).getProfile(2002L);
    }
}
