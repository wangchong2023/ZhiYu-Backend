package com.zhiyu.common.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SensitiveDataConverterTest {

    private final SensitiveDataConverter converter = new SensitiveDataConverter();

    @Test
    void shouldMaskPhoneNumber() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getFormattedMessage()).thenReturn("用户 13812348000 登录成功");

        String result = converter.convert(event);

        assertThat(result).isEqualTo("用户 138****8000 登录成功");
    }

    @Test
    void shouldMaskMultiplePhoneNumbers() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getFormattedMessage()).thenReturn("13800001111 给 15912348888 发送消息");

        String result = converter.convert(event);

        assertThat(result).isEqualTo("138****1111 给 159****8888 发送消息");
    }

    @Test
    void shouldMaskEmail() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getFormattedMessage()).thenReturn("用户 test@example.com 注册");

        String result = converter.convert(event);

        assertThat(result).isEqualTo("用户 tes***@example.com 注册");
    }

    @Test
    void shouldMaskEmailWithLongUsername() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getFormattedMessage()).thenReturn("发送邮件到 abc123@test.org");

        String result = converter.convert(event);

        assertThat(result).isEqualTo("发送邮件到 abc***@test.org");
    }

    @Test
    void shouldMaskEmailWithShortUsername() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getFormattedMessage()).thenReturn("ab@example.com 验证");

        String result = converter.convert(event);

        assertThat(result).isEqualTo("ab***@example.com 验证");
    }

    @Test
    void shouldMaskBothPhoneAndEmail() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getFormattedMessage()).thenReturn("user@domain.com 手机 13900001111 同时出现");

        String result = converter.convert(event);

        assertThat(result).isEqualTo("use***@domain.com 手机 139****1111 同时出现");
    }

    @Test
    void shouldReturnNullForNullMessage() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getFormattedMessage()).thenReturn(null);

        String result = converter.convert(event);

        assertThat(result).isNull();
    }

    @Test
    void shouldNotModifyMessageWithoutSensitiveData() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getFormattedMessage()).thenReturn("这是一条普通日志，没有敏感信息");

        String result = converter.convert(event);

        assertThat(result).isEqualTo("这是一条普通日志，没有敏感信息");
    }

    @Test
    void shouldMaskPhoneInDifferentFormats() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getFormattedMessage()).thenReturn("phone=13011112222");

        String result = converter.convert(event);

        assertThat(result).isEqualTo("phone=130****2222");
    }
}
