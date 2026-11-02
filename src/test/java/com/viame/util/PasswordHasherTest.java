package com.viame.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PasswordHasher Security Utility Tests")
class PasswordHasherTest {

    @Test
    @DisplayName("BCrypt hashing produces valid salted hash format")
    void testHashingFormat() {
        String plain = "Password123!";
        String hash = PasswordHasher.hash(plain);

        assertNotNull(hash);
        assertTrue(hash.startsWith("$2a$12$"), "Hash should use BCrypt cost factor 12");
        assertEquals(60, hash.length(), "Standard BCrypt hash string is 60 characters");
    }

    @Test
    @DisplayName("Password verification succeeds for correct password and fails for wrong")
    void testVerification() {
        String plain = "SecureSecret!45";
        String hash = PasswordHasher.hash(plain);

        assertTrue(PasswordHasher.verify(plain, hash));
        assertFalse(PasswordHasher.verify("WrongPassword123", hash));
        assertFalse(PasswordHasher.verify("", hash));
        assertFalse(PasswordHasher.verify(null, hash));
        assertFalse(PasswordHasher.verify(plain, null));
    }

    @Test
    @DisplayName("Salt extraction returns standard BCrypt salt prefix")
    void testSaltExtraction() {
        String hash = PasswordHasher.hash("MyPassword@99");
        String salt = PasswordHasher.extractSalt(hash);

        assertNotNull(salt);
        assertEquals(29, salt.length());
        assertTrue(salt.startsWith("$2a$12$"));
    }
}

