package com.himalayanvault.auth;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.himalayanvault.db.DatabaseManager;
/**
 * AuthManager — verifies the master password using PBKDF2-HMAC-SHA256.
 *
 * In a real implementation, the salt and verifier are loaded from
 * the encrypted SQLite vault database.
 */
public class AuthManager {

    private static final String ALGORITHM  = "PBKDF2WithHmacSHA256";
    private static final int    ITERATIONS = 100_000;
    private static final int    KEY_LENGTH = 256; // bits

    /**
     * Verifies the entered password against the stored PBKDF2 hash for a specific user.
     *
     * @param username         the username
     * @param enteredPassword  the raw password typed by the user
     * @return true if the password matches the stored verifier
     */
    public boolean verifyMasterPassword(String username, String enteredPassword) {
        try {
            byte[] salt = DatabaseManager.getInstance().loadSalt(username);
            byte[] storedHash = DatabaseManager.getInstance().loadPasswordHash(username);
            
            // User doesn't exist if either salt or hash is missing
            if (salt == null || storedHash == null) {
                System.err.println("[AuthManager] User '" + username + "' not found or password not set");
                return false;
            }

            byte[] derivedHash = pbkdf2(enteredPassword.toCharArray(), salt);
            boolean matches = Arrays.equals(derivedHash, storedHash);
            
            if (matches) {
                System.out.println("[AuthManager] Password verified for user: " + username);
            } else {
                System.err.println("[AuthManager] Password verification FAILED for user: " + username);
            }
            
            return matches;

        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            System.err.println("[AuthManager] Key derivation error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Hashes a new master password and stores salt + hash in the database for a specific user.
     *
     * @param username         the username
     * @param masterPassword   the new master password to set
     */
    public void setMasterPassword(String username, String masterPassword)
            throws NoSuchAlgorithmException, InvalidKeySpecException {

        byte[] salt = generateSalt();
        byte[] hash = pbkdf2(masterPassword.toCharArray(), salt);

        // Persist to database
        String saltBase64 = Base64.getEncoder().encodeToString(salt);
        String hashBase64 = Base64.getEncoder().encodeToString(hash);
        DatabaseManager.getInstance().saveMasterPassword(username, saltBase64, hashBase64);

        System.out.println("[AuthManager] Master password set for user: " + username);
    }
    /**
     * Resets the master password after verification of recovery code for a specific user.
     * Used in password recovery flow.
     *
     * @param username    the username
     * @param newPassword the new master password to set
     * @return true if password was successfully reset
     */
    public boolean resetPasswordWithRecovery(String username, String newPassword) {
        try {
            setMasterPassword(username, newPassword);
            System.out.println("[AuthManager] Password reset successfully via recovery code for user: " + username);
            return true;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            System.err.println("[AuthManager] Error resetting password: " + e.getMessage());
            return false;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────

    private byte[] pbkdf2(char[] password, byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {

        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            return skf.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private byte[] generateSalt() {
        byte[] salt = new byte[32];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

}
