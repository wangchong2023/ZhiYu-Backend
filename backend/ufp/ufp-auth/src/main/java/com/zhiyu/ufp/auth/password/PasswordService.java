package com.zhiyu.ufp.auth.password;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordService {

    private final BCryptPasswordEncoder encoder;

    public PasswordService() {
        this(12);
    }

    public PasswordService(int strength) {
        this.encoder = new BCryptPasswordEncoder(strength);
    }

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean verify(String rawPassword, String hashedPassword) {
        return encoder.matches(rawPassword, hashedPassword);
    }
}
