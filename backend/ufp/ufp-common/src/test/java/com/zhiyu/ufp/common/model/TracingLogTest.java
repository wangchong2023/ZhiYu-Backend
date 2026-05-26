package com.zhiyu.ufp.common.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TracingLogTest {

    @Test
    void shouldBuildTracingLogWithAllFields() {
        TracingLog log = TracingLog.builder()
                .appName("zhiyu-auth")
                .module("auth")
                .className("AuthController")
                .methodName("login")
                .operation("LOGIN")
                .description("User login")
                .ipAddress("192.168.1.1")
                .requestUri("/api/v1/auth/login")
                .requestParams("[username=test]")
                .response("LoginResponse(token=...)")
                .duration(150L)
                .status("OK")
                .errorMessage(null)
                .timestamp(1716019200000L)
                .build();

        assertThat(log.getAppName()).isEqualTo("zhiyu-auth");
        assertThat(log.getModule()).isEqualTo("auth");
        assertThat(log.getClassName()).isEqualTo("AuthController");
        assertThat(log.getMethodName()).isEqualTo("login");
        assertThat(log.getOperation()).isEqualTo("LOGIN");
        assertThat(log.getDuration()).isEqualTo(150L);
        assertThat(log.getStatus()).isEqualTo("OK");
        assertThat(log.getErrorMessage()).isNull();
    }

    @Test
    void shouldBuildTracingLogWithError() {
        TracingLog log = TracingLog.builder()
                .className("AuthService")
                .methodName("validatePassword")
                .operation("VALIDATE")
                .duration(10L)
                .status("FAILED")
                .errorMessage("Invalid credentials")
                .timestamp(System.currentTimeMillis())
                .build();

        assertThat(log.getStatus()).isEqualTo("FAILED");
        assertThat(log.getErrorMessage()).isEqualTo("Invalid credentials");
    }

    @Test
    void shouldSupportPartialBuild() {
        TracingLog log = TracingLog.builder()
                .className("TestClass")
                .methodName("testMethod")
                .timestamp(0L)
                .build();

        assertThat(log.getClassName()).isEqualTo("TestClass");
        assertThat(log.getMethodName()).isEqualTo("testMethod");
        assertThat(log.getStatus()).isNull();
        assertThat(log.getDuration()).isZero();
    }
}
