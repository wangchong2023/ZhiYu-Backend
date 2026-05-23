package com.zhiyu.ufp.auth.password;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordService {

    private static final int DEFAULT_BCRYPT_STRENGTH = 12;

    private final BCryptPasswordEncoder encoder;

    public PasswordService() {
        this(DEFAULT_BCRYPT_STRENGTH);
    }

    public PasswordService(final int strength) {
        this.encoder = new BCryptPasswordEncoder(strength);
    }

    public String hash(final String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean verify(final String rawPassword, final String hashedPassword) {
        return encoder.matches(rawPassword, hashedPassword);
    }
}
