package com.zhiyu.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateTemplateRequest {
    @NotBlank
    private String subject;

    @NotBlank
    private String body;

    private String variablesJson;

    private String description;

    private Boolean isActive;
}
