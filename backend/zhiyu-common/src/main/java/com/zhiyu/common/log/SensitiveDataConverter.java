package com.zhiyu.common.log;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Pattern;

public class SensitiveDataConverter extends MessageConverter {

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(1[3-9]\\d)\\d{4}(\\d{4})");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("([a-zA-Z0-9._%+-]{1,3})[a-zA-Z0-9._%+-]*(@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");

    @Override
    public String convert(final ILoggingEvent event) {
        String message = event.getFormattedMessage();
        if (message == null) {
            return null;
        }
        message = PHONE_PATTERN.matcher(message).replaceAll("$1****$2");
        message = EMAIL_PATTERN.matcher(message).replaceAll("$1***$2");
        return message;
    }
}
