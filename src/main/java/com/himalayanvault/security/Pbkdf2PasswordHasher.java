package com.himalayanvault.security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Stores master password verifiers as pbkdf2-sha256$iterations$salt$hash.
 * The random per-user salt is embedded in the stored value so the existing
 * SQLite schema does not need to change.
 */
public final class Pbkdf2PasswordHasher {

    public static final int ITERATIONS = 310_000;
    private static final int SALT_BYTES = 32;
    private static final int HASH_BITS = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2-sha256";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Pbkdf2PasswordHasher() {
    }

    public static String hashPassword(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = derive(password, salt, ITERATIONS);
        return PREFIX + "$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    public static boolean verifyPassword(String storedHash, String password) {
        try {
            String[] parts = storedHash.split("\\$");
            if (parts.length != 4 || !PREFIX.equals(parts[0])) {
                return false;
            }
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Returns the Base64 salt embedded in a stored PBKDF2 hash, or empty if unavailable. */
    public static String extractEmbeddedSalt(String storedHash) {
        try {
            String[] parts = storedHash.split("\\$");
            if (parts.length == 4 && PREFIX.equals(parts[0])) {
                return parts[2];
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return "";
    }

    public static boolean needsRehash(String storedHash) {
        try {
            String[] parts = storedHash.split("\\$");
            return parts.length != 4 || !PREFIX.equals(parts[0]) || Integer.parseInt(parts[1]) < ITERATIONS;
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
            try {
                return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
            } finally {
                spec.clearPassword();
            }
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 password hashing failed", e);
        }
    }
}
