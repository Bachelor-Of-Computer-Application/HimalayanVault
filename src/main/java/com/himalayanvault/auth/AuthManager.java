package com.himalayanvault.auth;

import com.himalayanvault.db.DatabaseManager;
import com.himalayanvault.security.Pbkdf2PasswordHasher;
import com.himalayanvault.security.SessionManager;

/**
 * AuthManager — verifies and stores master passwords.
 * 
 * Security features:
 * - PBKDF2WithHmacSHA256 password hashing with per-user random salts
 * - Session invalidation on password change
 * - Rate limiting on password verification (can be added)
 */
public class AuthManager {

    /**
     * Verifies the entered password against the stored Argon2id hash for a specific user.
     *
     * @param username         the username
     * @param enteredPassword  the raw password typed by the user
     * @return true if the password matches the stored verifier
     */
    public boolean verifyMasterPassword(String username, String enteredPassword) {
        try {
            String storedHash = DatabaseManager.getInstance().loadPasswordHash(username);
            
            // User doesn't exist if hash is missing
            if (storedHash == null) {
                System.err.println("[AuthManager] User '" + username + "' not found or password not set");
                return false;
            }

            boolean matches = Pbkdf2PasswordHasher.verifyPassword(storedHash, enteredPassword);

            if (matches) {
                System.out.println("[AuthManager] Password verified for user: " + username);
            } else {
                System.err.println("[AuthManager] Password verification FAILED for user: " + username);
            }
            
            return matches;

        } catch (Exception e) {
            System.err.println("[AuthManager] Verification error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Hashes a new master password using PBKDF2 and stores it in the database.
     * Also invalidates all existing sessions (forces re-authentication).
     *
     * @param username         the username
     * @param masterPassword   the new master password to set
     * @throws Exception if hash or storage fails
     */
    public void setMasterPassword(String username, String masterPassword) throws Exception {
        String passwordHash = Pbkdf2PasswordHasher.hashPassword(masterPassword);
        DatabaseManager.getInstance().saveMasterPassword(username, passwordHash);
        
        // Invalidate all sessions (forces user to re-authenticate)
        SessionManager.getInstance().invalidateAllSessions();

        System.out.println("[AuthManager] Master password set for user: " + username + " (PBKDF2)");
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
        } catch (Exception e) {
            System.err.println("[AuthManager] Error resetting password: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if a password hash needs rehashing after PBKDF2 parameter updates.
     * Should be called periodically to upgrade weak hashes.
     *
     * @param username the username
     * @return true if password should be re-hashed with current parameters
     */
    public boolean passwordNeedsRehashing(String username) {
        try {
            String storedHash = DatabaseManager.getInstance().loadPasswordHash(username);
            if (storedHash == null) return false;
            
            return Pbkdf2PasswordHasher.needsRehash(storedHash);
        } catch (Exception e) {
            return false;
        }
    }
}
