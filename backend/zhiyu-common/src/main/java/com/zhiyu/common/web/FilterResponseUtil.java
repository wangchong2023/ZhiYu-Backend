package com.zhiyu.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;

public final class FilterResponseUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FilterResponseUtil() {
    }

    public static void writeError(final HttpServletResponse response,
                                   final int status, final int code,
                                   final String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String json = MAPPER.writeValueAsString(ApiResponse.fail(code, message));
        response.getWriter().write(json);
    }
}
