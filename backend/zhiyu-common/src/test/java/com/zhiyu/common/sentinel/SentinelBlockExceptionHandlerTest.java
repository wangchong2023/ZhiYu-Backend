package com.zhiyu.common.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SentinelBlockExceptionHandlerTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private SentinelBlockExceptionHandler handler;
    private StringWriter stringWriter;

    @BeforeEach
    void setUp() throws IOException {
        handler = new SentinelBlockExceptionHandler(new ObjectMapper());
        stringWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Test
    void shouldReturn429ForFlowException() throws IOException {
        handler.handle(request, response, "login", new FlowException("login", "too many"));
        String json = stringWriter.toString();
        assertThat(json).contains("\"code\":42902");
        assertThat(json).contains(BizErrorCode.TOO_MANY_REQUESTS.getMessage());
    }

    @Test
    void shouldReturn429ForParamFlowException() throws IOException {
        handler.handle(request, response, "login", new ParamFlowException("login", "param limit"));
        String json = stringWriter.toString();
        assertThat(json).contains("\"code\":42902");
    }

    @Test
    void shouldReturn503ForDegradeException() throws IOException {
        handler.handle(request, response, "login", new DegradeException("login", "circuit open"));
        String json = stringWriter.toString();
        assertThat(json).contains("\"code\":50301");
        assertThat(json).contains(BizErrorCode.SERVICE_UNAVAILABLE.getMessage());
    }

    @Test
    void shouldReturn503ForSystemBlockException() throws IOException {
        handler.handle(request, response, "login", new SystemBlockException("login", "system"));
        String json = stringWriter.toString();
        assertThat(json).contains("\"code\":50301");
        assertThat(json).contains(BizErrorCode.SERVICE_UNAVAILABLE.getMessage());
    }

    @Test
    void shouldReturn403ForAuthorityException() throws IOException {
        handler.handle(request, response, "admin", new AuthorityException("admin", "denied"));
        String json = stringWriter.toString();
        assertThat(json).contains("\"code\":40301");
        assertThat(json).contains(BizErrorCode.ACCESS_DENIED.getMessage());
    }

    @Test
    void shouldReturn429ForUnknownBlockException() throws IOException {
        BlockException unknown = new BlockException("login", "unknown") {};
        handler.handle(request, response, "unknown", unknown);
        String json = stringWriter.toString();
        assertThat(json).contains("\"code\":42902");
    }
}
