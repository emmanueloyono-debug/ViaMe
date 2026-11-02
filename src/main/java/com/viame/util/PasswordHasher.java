package com.viame.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * BCrypt password hashing utility (adaptive, salted, cost factor 12).
 *
 * <p>BCrypt embeds its random salt inside the hash string, so {@link #verify} needs only
 * the stored hash. {@link #extractSalt} exposes the salt portion for the schema's
 * {@code password_salt} column (kept for audit / defense purposes).</p>
 */
public final class PasswordHasher {

    private static final int COST = 12;
    private static final int SALT_LENGTH = 29; // "$2a$12$" + 22 base64 chars

    private PasswordHasher() {
    }

    public static String hash(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(COST));
    }

    /**
     * @return {@code true} if the password matches; {@code false} on mismatch or a malformed hash
     */
    public static boolean verify(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null) {
            return false;
        }
        try {
            return BCrypt.checkpw(plainPassword, storedHash);
        } catch (IllegalArgumentException malformedHash) {
            return false;
        }
    }

    public static String extractSalt(String bcryptHash) {
        if (bcryptHash == null || bcryptHash.length() < SALT_LENGTH) {
            throw new IllegalArgumentException("Not a BCrypt hash");
        }
        return bcryptHash.substring(0, SALT_LENGTH);
    }
}

