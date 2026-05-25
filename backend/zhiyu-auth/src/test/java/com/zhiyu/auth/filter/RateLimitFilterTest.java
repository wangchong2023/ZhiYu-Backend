package com.zhiyu.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private FilterChain chain;

    @InjectMocks
    private RateLimitFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ── First Request (count == 1) ────────────────────────────

    @Test
    void shouldPassFirstRequestAndSetExpire() throws ServletException, IOException {
        request.setRemoteAddr("192.168.1.1");

        when(valueOps.increment("rate:ip:192.168.1.1")).thenReturn(1L);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(redisTemplate).expire("rate:ip:192.168.1.1", 60, TimeUnit.SECONDS);
    }

    // ── Request Under Limit ───────────────────────────────────

    @Test
    void shouldPassRequestUnderLimit() throws ServletException, IOException {
        request.setRemoteAddr("192.168.1.2");

        when(valueOps.increment("rate:ip:192.168.1.2")).thenReturn(50L);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void shouldPassRequestAtExactLimit() throws ServletException, IOException {
        request.setRemoteAddr("192.168.1.3");

        when(valueOps.increment("rate:ip:192.168.1.3")).thenReturn(100L);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ── Rate Limit Exceeded ───────────────────────────────────

    @Test
    void shouldRejectWhenRateLimitExceeded() throws ServletException, IOException {
        request.setRemoteAddr("192.168.1.4");

        when(valueOps.increment("rate:ip:192.168.1.4")).thenReturn(101L);

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
        verify(objectMapper).writeValue(any(java.io.Writer.class), any(Object.class));
    }

    @Test
    void shouldRejectAtWayAboveLimit() throws ServletException, IOException {
        request.setRemoteAddr("192.168.1.5");

        when(valueOps.increment("rate:ip:192.168.1.5")).thenReturn(500L);

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(429);
    }

    // ── Different IPs Have Separate Counters ──────────────────

    @Test
    void shouldTrackDifferentIpsSeparately() throws ServletException, IOException {
        request.setRemoteAddr("10.0.0.1");

        when(valueOps.increment("rate:ip:10.0.0.1")).thenReturn(1L);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(redisTemplate).expire("rate:ip:10.0.0.1", 60, TimeUnit.SECONDS);
    }

    // ── Null Count Handling ───────────────────────────────────

    @Test
    void shouldHandleNullIncrementResult() throws ServletException, IOException {
        request.setRemoteAddr("192.168.1.6");

        when(valueOps.increment("rate:ip:192.168.1.6")).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        // count defaults to 0L, so 0 > 100 is false → pass through
        verify(chain).doFilter(request, response);
    }

    // ── All IPs get independent keys ──────────────────────────

    @Test
    void shouldCreateUniqueKeyForEachIp() throws ServletException, IOException {
        request.setRemoteAddr("172.16.0.1");
        when(valueOps.increment("rate:ip:172.16.0.1")).thenReturn(1L);

        filter.doFilterInternal(request, response, chain);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).expire(keyCaptor.capture(), eq(60L), eq(TimeUnit.SECONDS));
        assertThat(keyCaptor.getValue()).isEqualTo("rate:ip:172.16.0.1");
    }
}
