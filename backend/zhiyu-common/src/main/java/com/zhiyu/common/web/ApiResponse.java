package com.zhiyu.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    private String requestId;
    private long timestamp;

    public static <T> ApiResponse<T> success(final T data) {
        return ApiResponse.<T>builder()
                .code(0)
                .message("success")
                .data(data)
                .requestId(UUID.randomUUID().toString().replace("-", ""))
                .timestamp(Instant.now().getEpochSecond())
                .build();
    }

    public static <T> ApiResponse<T> fail(final int code, final String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .requestId(UUID.randomUUID().toString().replace("-", ""))
                .timestamp(Instant.now().getEpochSecond())
                .build();
    }
}
