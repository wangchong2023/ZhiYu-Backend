/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

package com.zhiyu.ufp.common.feign;

import com.zhiyu.ufp.common.exception.BizException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feign 异常解码器测试类。
 *
 * <p>测试通过 Feign 调用时对远端错误响应的解析转换，验证提取的 code 和 message 能正确反序列化为本地 BizException。</p>
 *
 * @author ZhiYu
 * @since 1.0.0
 */
class FeignErrorDecoderTest {

    private final FeignErrorDecoder decoder = new FeignErrorDecoder();

    /**
     * 测试当返回标准的错误 JSON Body 时，解码器是否能反解出 BizException。
     */
    @Test
    void shouldDecodeToBizExceptionWhenResponseBodyContainsCodeAndMessage() {
        String jsonError = "{\"code\":40105,\"message\":\"Incorrect password\"}";
        Response response = Response.builder()
                .status(400)
                .reason("Bad Request")
                .request(Request.create(Request.HttpMethod.GET, "/api/test", Collections.emptyMap(), null, StandardCharsets.UTF_8, null))
                .body(jsonError, StandardCharsets.UTF_8)
                .build();

        Exception decoded = decoder.decode("TestService#testMethod()", response);

        assertThat(decoded).isInstanceOf(BizException.class);
        BizException bizEx = (BizException) decoded;
        assertThat(bizEx.getCode()).isEqualTo(40105);
        assertThat(bizEx.getMessage()).isEqualTo("Incorrect password");
    }

    /**
     * 测试当返回的 Response 没有 Body 时，解码器应退化返回默认异常。
     */
    @Test
    void shouldReturnDefaultExceptionWhenResponseBodyIsNull() {
        Response response = Response.builder()
                .status(500)
                .reason("Internal Server Error")
                .request(Request.create(Request.HttpMethod.GET, "/api/test", Collections.emptyMap(), null, StandardCharsets.UTF_8, null))
                .build();

        Exception decoded = decoder.decode("TestService#testMethod()", response);

        assertThat(decoded).isNotNull().isNotInstanceOf(BizException.class);
    }
}
