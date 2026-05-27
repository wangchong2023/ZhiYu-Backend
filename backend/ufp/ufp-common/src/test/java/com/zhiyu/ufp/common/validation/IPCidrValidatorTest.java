/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: IPCidrValidatorTest.java
 * 创建时间: 2026-05-27
 * 描述: IPCidrValidator 单元测试类，覆盖各种合法/非法的 IPv4 / IPv6 网段格式。
 */
package com.zhiyu.ufp.common.validation;
 
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
 
import static org.assertj.core.api.Assertions.assertThat;
 
/**
 * 类名: IPCidrValidatorTest
 * 描述: 测试 CIDR 网段校验器。通过参数化测试验证各类常见网段及边界场景，保证高代码覆盖率和健壮性。
 */
class IPCidrValidatorTest {
 
    private IPCidrValidator validator;
 
    @BeforeEach
    void setUp() {
        validator = new IPCidrValidator();
    }
 
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n", "\t"})
    void shouldReturnTrueWhenInputIsEmptyOrNull(final String input) {
        boolean result = validator.isValid(input, null);
        assertThat(result).isTrue();
    }
 
    @ParameterizedTest
    @ValueSource(strings = {
            "192.168.1.0/24",
            "10.0.0.0/8",
            "172.16.0.0/12",
            "0.0.0.0/0",
            "127.0.0.1",
            "2001:db8::/32",
            "fe80::/10",
            "::1",
            "192.168.1.1/32"
    })
    void shouldReturnTrueWhenInputIsValidCidr(final String input) {
        boolean result = validator.isValid(input, null);
        assertThat(result).isTrue();
    }
 
    @ParameterizedTest
    @ValueSource(strings = {
            "256.0.0.1/24",
            "192.168.1.0/33",
            "invalid-ip",
            "192.168.1.",
            "192.168.1.0/",
            "2001:db8::/129",
            "192.168.1.0/abc",
            "192.168.1.256"
    })
    void shouldReturnFalseWhenInputIsInvalidCidr(final String input) {
        boolean result = validator.isValid(input, null);
        assertThat(result).isFalse();
    }
}
