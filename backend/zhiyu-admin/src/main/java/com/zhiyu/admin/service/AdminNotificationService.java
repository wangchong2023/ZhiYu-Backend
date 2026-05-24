package com.zhiyu.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.admin.dto.NotificationTemplateDto;
import com.zhiyu.admin.dto.UpdateTemplateRequest;
import com.zhiyu.notification.entity.NotificationTemplate;
import com.zhiyu.notification.mapper.NotificationTemplateMapper;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminNotificationService {

    private static final int ERR_TEMPLATE_NOT_FOUND = 40401;

    private final NotificationTemplateMapper templateMapper;

    public List<NotificationTemplateDto> listTemplates() {
        return templateMapper.selectList(
                        new LambdaQueryWrapper<NotificationTemplate>()
                                .orderByAsc(NotificationTemplate::getType)
                                .orderByAsc(NotificationTemplate::getTemplateKey))
                .stream()
                .map(t -> NotificationTemplateDto.builder()
                        .id(t.getId())
                        .templateKey(t.getTemplateKey())
                        .type(t.getType())
                        .subject(t.getSubject())
                        .body(t.getBody())
                        .variablesJson(t.getVariablesJson())
                        .description(t.getDescription())
                        .isActive(t.getIsActive())
                        .createdAt(t.getCreatedAt())
                        .updatedAt(t.getUpdatedAt())
                        .build())
                .toList();
    }

    public NotificationTemplateDto getTemplate(final Long id) {
        NotificationTemplate t = templateMapper.selectById(id);
        if (t == null) {
            throw new BizException(ERR_TEMPLATE_NOT_FOUND, "模板不存在");
        }
        return NotificationTemplateDto.builder()
                .id(t.getId())
                .templateKey(t.getTemplateKey())
                .type(t.getType())
                .subject(t.getSubject())
                .body(t.getBody())
                .variablesJson(t.getVariablesJson())
                .description(t.getDescription())
                .isActive(t.getIsActive())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateTemplate(final Long id, final UpdateTemplateRequest request) {
        NotificationTemplate t = templateMapper.selectById(id);
        if (t == null) {
            throw new BizException(ERR_TEMPLATE_NOT_FOUND, "模板不存在");
        }
        t.setSubject(request.getSubject());
        t.setBody(request.getBody());
        if (request.getVariablesJson() != null) {
            t.setVariablesJson(request.getVariablesJson());
        }
        if (request.getDescription() != null) {
            t.setDescription(request.getDescription());
        }
        if (request.getIsActive() != null) {
            t.setIsActive(request.getIsActive());
        }
        templateMapper.updateById(t);
    }
}
