package com.zhiyu.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldCreateSuccessResponse() {
        ApiResponse<String> resp = ApiResponse.success("hello");

        assertThat(resp.getCode()).isZero();
        assertThat(resp.getMessage()).isEqualTo("success");
        assertThat(resp.getData()).isEqualTo("hello");
        assertThat(resp.getRequestId()).isNotBlank();
        assertThat(resp.getTimestamp()).isPositive();
    }

    @Test
    void shouldCreateSuccessResponseWithNullData() {
        ApiResponse<Void> resp = ApiResponse.success(null);

        assertThat(resp.getCode()).isZero();
        assertThat(resp.getData()).isNull();
    }

    @Test
    void shouldCreateFailResponse() {
        ApiResponse<Void> resp = ApiResponse.fail(40001, "Validation failed");

        assertThat(resp.getCode()).isEqualTo(40001);
        assertThat(resp.getMessage()).isEqualTo("Validation failed");
        assertThat(resp.getData()).isNull();
        assertThat(resp.getRequestId()).isNotBlank();
        assertThat(resp.getTimestamp()).isPositive();
    }

    @Test
    void shouldHaveUniqueRequestIds() {
        ApiResponse<String> r1 = ApiResponse.success("a");
        ApiResponse<String> r2 = ApiResponse.success("b");

        assertThat(r1.getRequestId()).isNotEqualTo(r2.getRequestId());
    }

    @Test
    void shouldUseBuilderDirectly() {
        ApiResponse<Integer> resp = ApiResponse.<Integer>builder()
                .code(0)
                .message("ok")
                .data(42)
                .requestId("test-id")
                .timestamp(1234567890L)
                .build();

        assertThat(resp.getCode()).isZero();
        assertThat(resp.getMessage()).isEqualTo("ok");
        assertThat(resp.getData()).isEqualTo(42);
        assertThat(resp.getRequestId()).isEqualTo("test-id");
        assertThat(resp.getTimestamp()).isEqualTo(1234567890L);
    }

    @Test
    void shouldOmitNullDataInJson() throws Exception {
        ApiResponse<Void> resp = ApiResponse.fail(40001, "error");
        String json = mapper.writeValueAsString(resp);

        assertThat(json).doesNotContain("\"data\"");
    }

    @Test
    void shouldIncludeDataInJsonWhenNotNull() throws Exception {
        ApiResponse<String> resp = ApiResponse.success("hello");
        String json = mapper.writeValueAsString(resp);

        assertThat(json).contains("\"data\":\"hello\"");
    }
}
