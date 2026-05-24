package com.zhiyu.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.admin.dto.NotificationTemplateDto;
import com.zhiyu.admin.dto.UpdateTemplateRequest;
import com.zhiyu.notification.entity.NotificationTemplate;
import com.zhiyu.notification.mapper.NotificationTemplateMapper;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminNotificationServiceTest {

    @Mock private NotificationTemplateMapper templateMapper;
    @InjectMocks private AdminNotificationService adminNotificationService;

    @Test
    void shouldListTemplates() {
        NotificationTemplate t = NotificationTemplate.builder()
                .id(1L).templateKey("sms_login").type("SMS")
                .subject("Login").isActive(true).build();
        when(templateMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(t));

        List<NotificationTemplateDto> result = adminNotificationService.listTemplates();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTemplateKey()).isEqualTo("sms_login");
    }

    @Test
    void shouldGetTemplate() {
        NotificationTemplate t = NotificationTemplate.builder()
                .id(1L).templateKey("sms_login").type("SMS")
                .subject("Login").body("Your code is {{code}}").isActive(true).build();
        when(templateMapper.selectById(1L)).thenReturn(t);

        NotificationTemplateDto result = adminNotificationService.getTemplate(1L);

        assertThat(result.getTemplateKey()).isEqualTo("sms_login");
    }

    @Test
    void shouldThrowWhenTemplateNotFound() {
        when(templateMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> adminNotificationService.getTemplate(99L))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldUpdateTemplate() {
        NotificationTemplate t = NotificationTemplate.builder()
                .id(1L).templateKey("sms_login").type("SMS")
                .subject("Old").body("Old body").isActive(true).build();
        when(templateMapper.selectById(1L)).thenReturn(t);

        UpdateTemplateRequest req = new UpdateTemplateRequest();
        req.setSubject("New Subject");
        req.setBody("New body {{code}}");
        req.setIsActive(false);
        adminNotificationService.updateTemplate(1L, req);

        assertThat(t.getSubject()).isEqualTo("New Subject");
        assertThat(t.getIsActive()).isFalse();
        verify(templateMapper).updateById(t);
    }
}
