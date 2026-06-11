package com.himalayanvault.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import javax.crypto.SecretKey;

/**
 * Derives a stable per-user encryption salt so credentials saved from the
 * desktop app and browser extension use the same AES key after unlock.
 */
public final class CredentialKeyDerivation {

    private CredentialKeyDerivation() {
    }

    public static byte[] saltForUser(String username) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    ("himalayan-vault-credential-key|" + username).getBytes(StandardCharsets.UTF_8));
            return Arrays.copyOf(hash, 16);
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive credential salt", e);
        }
    }

    public static SecretKey keyForUser(String username, String masterPassword) {
        return EncryptionUtil.deriveKeyFromPassword(masterPassword, saltForUser(username));
    }
}
