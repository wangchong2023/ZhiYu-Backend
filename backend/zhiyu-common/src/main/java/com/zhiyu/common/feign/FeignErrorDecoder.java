package com.zhiyu.common.feign;

import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();
    private static final int HTTP_SERVER_ERROR_MIN = 500;

    @Override
    public Exception decode(final String methodKey, final Response response) {
        if (log.isErrorEnabled()) {
            log.error("Feign call failed: method={}, status={}", methodKey, response.status());
        }
        if (response.status() >= HTTP_SERVER_ERROR_MIN) {
            return new BizException(BizErrorCode.INTERNAL_ERROR);
        }
        return defaultDecoder.decode(methodKey, response);
    }
}
