package com.zhiyu.admin.controller;

import com.zhiyu.admin.dto.NotificationTemplateDto;
import com.zhiyu.admin.dto.UpdateTemplateRequest;
import com.zhiyu.admin.service.AdminNotificationService;
import com.zhiyu.common.web.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminNotificationControllerTest {

    @Mock private AdminNotificationService adminNotificationService;
    @InjectMocks private AdminNotificationController adminNotificationController;

    @Test
    void shouldListTemplates() {
        NotificationTemplateDto dto = NotificationTemplateDto.builder()
                .id(1L).templateKey("sms_login").type("SMS").build();
        when(adminNotificationService.listTemplates()).thenReturn(List.of(dto));

        ApiResponse<List<NotificationTemplateDto>> resp = adminNotificationController.listTemplates();

        assertThat(resp.getData()).hasSize(1);
    }

    @Test
    void shouldGetTemplate() {
        NotificationTemplateDto dto = NotificationTemplateDto.builder()
                .id(1L).templateKey("sms_login").type("SMS").build();
        when(adminNotificationService.getTemplate(1L)).thenReturn(dto);

        ApiResponse<NotificationTemplateDto> resp = adminNotificationController.getTemplate(1L);

        assertThat(resp.getData().getTemplateKey()).isEqualTo("sms_login");
    }

    @Test
    void shouldUpdateTemplate() {
        UpdateTemplateRequest req = new UpdateTemplateRequest();
        req.setSubject("New Subject");
        req.setBody("New body");

        ApiResponse<Void> resp = adminNotificationController.updateTemplate(1L, req);

        verify(adminNotificationService).updateTemplate(1L, req);
        assertThat(resp.getCode()).isZero();
    }
}
