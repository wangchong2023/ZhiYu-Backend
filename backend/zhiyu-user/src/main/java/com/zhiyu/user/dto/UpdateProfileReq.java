package com.zhiyu.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "更新资料请求")
public class UpdateProfileReq {

    private static final int NICK_MAX_LENGTH = 80;

    @Size(max = NICK_MAX_LENGTH)
    @Schema(description = "昵称", example = "新昵称", maxLength = NICK_MAX_LENGTH)
    private String nick;
}
