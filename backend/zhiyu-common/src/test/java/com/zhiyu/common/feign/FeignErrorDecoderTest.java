package com.zhiyu.common.feign;

import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class FeignErrorDecoderTest {

    private final FeignErrorDecoder decoder = new FeignErrorDecoder();

    @Test
    void shouldReturnBizExceptionFor500() {
        Response response = Response.builder()
                .status(500)
                .reason("Internal Server Error")
                .request(Request.create(Request.HttpMethod.GET, "http://test",
                        Collections.emptyMap(), null, null, null))
                .headers(Map.of())
                .body(new byte[0])
                .build();

        Exception ex = decoder.decode("AuthClient#getUserById(Long)", response);
        assertThat(ex).isInstanceOf(BizException.class);
        BizException bizEx = (BizException) ex;
        assertThat(bizEx.getErrorCode()).isEqualTo(BizErrorCode.INTERNAL_ERROR);
    }

    @Test
    void shouldReturnBizExceptionFor502() {
        Response response = Response.builder()
                .status(502)
                .reason("Bad Gateway")
                .request(Request.create(Request.HttpMethod.GET, "http://test",
                        Collections.emptyMap(), null, null, null))
                .headers(Map.of())
                .body(new byte[0])
                .build();

        Exception ex = decoder.decode("AuthClient#getUserById(Long)", response);
        assertThat(ex).isInstanceOf(BizException.class);
    }

    @Test
    void shouldReturnBizExceptionFor503() {
        Response response = Response.builder()
                .status(503)
                .reason("Service Unavailable")
                .request(Request.create(Request.HttpMethod.GET, "http://test",
                        Collections.emptyMap(), null, null, null))
                .headers(Map.of())
                .body(new byte[0])
                .build();

        Exception ex = decoder.decode("AuthClient#getUserById(Long)", response);
        assertThat(ex).isInstanceOf(BizException.class);
    }

    @Test
    void shouldDelegateToDefaultFor400() {
        Response response = Response.builder()
                .status(400)
                .reason("Bad Request")
                .request(Request.create(Request.HttpMethod.GET, "http://test",
                        Collections.emptyMap(), null, null, null))
                .headers(Map.of())
                .body(new byte[0])
                .build();

        Exception ex = decoder.decode("AuthClient#getUserById(Long)", response);
        assertThat(ex).isNotInstanceOf(BizException.class);
    }

    @Test
    void shouldDelegateToDefaultFor404() {
        Response response = Response.builder()
                .status(404)
                .reason("Not Found")
                .request(Request.create(Request.HttpMethod.GET, "http://test",
                        Collections.emptyMap(), null, null, null))
                .headers(Map.of())
                .body(new byte[0])
                .build();

        Exception ex = decoder.decode("AuthClient#getUserById(Long)", response);
        assertThat(ex).isNotInstanceOf(BizException.class);
    }
}
