package com.pixledger;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordHashGeneratorTest {
    @Test
    void generatesVerifiableBcryptHash() {
        String password = "654321";
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String hash = encoder.encode(password);

        System.out.println("BCrypt hash: " + hash);
    }
}
