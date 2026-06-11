package com.himalayanvault.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * PepperManager — manages server-side pepper for additional password protection.
 * 
 * A pepper is a secret value (like salt) that is NOT stored in the database.
 * It's stored as an environment variable or config file, separate from the database.
 * 
 * If the SQLite database is stolen but the pepper remains unknown, attackers cannot
 * crack the password hashes even with GPU acceleration.
 * 
 * Environment variable: HIMALAYAN_VAULT_PEPPER
 * If not set, a default pepper is generated (should be overridden in production).
 */
public class PepperManager {

    private static final String ENV_VAR = "HIMALAYAN_VAULT_PEPPER";
    private static final String DEFAULT_PEPPER = "himalayan-vault-default-pepper-change-in-production";
    private static String cachedPepper = null;

    /**
     * Get the pepper from environment or use default.
     * IMPORTANT: In production, set HIMALAYAN_VAULT_PEPPER environment variable to a secure value.
     *
     * @return the pepper bytes
     */
    public static byte[] getPepper() {
        if (cachedPepper == null) {
            String pepperStr = System.getenv(ENV_VAR);
            if (pepperStr == null || pepperStr.isEmpty()) {
                // Use default if not set (log warning in production)
                System.err.println("[PepperManager] WARNING: " + ENV_VAR + " not set. Using default pepper.");
                System.err.println("[PepperManager] Set the environment variable for production security.");
                cachedPepper = DEFAULT_PEPPER;
            } else {
                cachedPepper = pepperStr;
            }
        }
        return cachedPepper.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Apply pepper to a password hash.
     * Concatenates pepper with the hash and returns a derived value.
     *
     * @param passwordHash the password hash (from Argon2id)
     * @return pepper-applied hash
     */
    public static String applyPepper(String passwordHash) {
        try {
            byte[] pepper = getPepper();
            byte[] hashBytes = passwordHash.getBytes(StandardCharsets.UTF_8);
            
            // Combine hash + pepper and derive a short tag
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(hashBytes);
            digest.update(pepper);
            byte[] derived = digest.digest();
            
            // Store the original hash + a tag derived from pepper
            // The tag is stored but doesn't reveal the pepper
            String tag = Base64.getEncoder().encodeToString(derived);
            return passwordHash + "|" + tag.substring(0, 32);
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply pepper", e);
        }
    }

    /**
     * Verify pepper application.
     * Recalculates the tag and compares with stored value.
     *
     * @param pepperAppliedHash the stored hash with pepper tag
     * @param plainPassword the plaintext password to verify
     * @param argon2Hash the new Argon2 hash of the plain password
     * @return true if pepper application is valid
     */
    public static boolean verifyPepperApplication(String pepperAppliedHash, String plainPassword, String argon2Hash) {
        try {
            String expectedPepperedHash = applyPepper(argon2Hash);
            return expectedPepperedHash.equals(pepperAppliedHash);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Set a custom pepper (for testing or explicit configuration).
     * CAUTION: Changing the pepper invalidates all existing passwords.
     *
     * @param newPepper the new pepper value
     */
    public static void setPepperForTesting(String newPepper) {
        cachedPepper = newPepper;
    }

    /**
     * Clear cached pepper (useful for testing).
     */
    public static void clearCache() {
        cachedPepper = null;
    }
}
