/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

package com.zhiyu.ufp.common.feign;

import com.zhiyu.ufp.common.exception.BizException;
import feign.Response;
import feign.Util;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Feign 服务间调用异常解码器。
 *
 * <p>用于拦截 Feign 调用返回的非 2xx HTTP 状态响应。
 * 读取 Response 报文中的 JSON 错误体，提取业务错误码 {@code code} 和消息 {@code message}，
 * 反解重构为本地的 {@link BizException} 抛出，以确保服务间调用发生异常时，异常上下文和错误码能顺利穿透分布式链路，
 * 避免抛出含糊的 500 熔断或网络异常。</p>
 *
 * @author ZhiYu
 * @since 1.0.0
 */
@Slf4j
public class FeignErrorDecoder implements ErrorDecoder {

    private static final Pattern CODE_PATTERN = Pattern.compile("\"code\"\\s*:\\s*(\\d+)");
    private static final Pattern MSG_PATTERN = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]+)\"");

    @Override
    public Exception decode(final String methodKey, final Response response) {
        if (response.body() != null) {
            try {
                // 读取原始的响应 JSON
                final String bodyStr = Util.toString(response.body().asReader(StandardCharsets.UTF_8));
                
                final Matcher codeMatcher = CODE_PATTERN.matcher(bodyStr);
                final Matcher msgMatcher = MSG_PATTERN.matcher(bodyStr);
                
                if (codeMatcher.find() && msgMatcher.find()) {
                    final int code = Integer.parseInt(codeMatcher.group(1));
                    final String msg = msgMatcher.group(1);
                    
                    if (log.isDebugEnabled()) {
                        log.debug("[FEIGN-DECODE] 反解异常 methodKey: {}, code: {}, msg: {}", methodKey, code, msg);
                    }
                    
                    return new BizException(code, msg);
                }
            } catch (IOException e) {
                if (log.isWarnEnabled()) {
                    log.warn("[FEIGN-DECODE] 读取 Feign 异常响应 Body 发生错误: {}", e.getMessage());
                }
            }
        }
        
        // 兜底返回 Feign 默认的错误解码结果
        return new ErrorDecoder.Default().decode(methodKey, response);
    }
}
