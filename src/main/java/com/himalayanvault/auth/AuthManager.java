package com.himalayanvault.auth;

import com.himalayanvault.db.DatabaseManager;
import com.himalayanvault.security.Pbkdf2PasswordHasher;
import com.himalayanvault.security.SessionManager;

/**
 * AuthManager — verifies and stores master passwords with brute-force lockout.
 */
public class AuthManager {

    public enum VerificationResult {
        SUCCESS,
        FAILED,
        LOCKED,
        USER_NOT_FOUND
    }

    private final AuthLockoutManager lockout = AuthLockoutManager.getInstance();

    /**
     * Verifies the master password and applies rate limiting.
     */
    public VerificationResult verifyMasterPassword(String username, String enteredPassword) {
        if (username == null || username.isBlank()) {
            return VerificationResult.FAILED;
        }

        String normalized = username.trim();
        if (lockout.isLocked(normalized)) {
            System.err.println("[AuthManager] Login blocked — user locked out: " + normalized);
            return VerificationResult.LOCKED;
        }

        try {
            String storedHash = DatabaseManager.getInstance().loadPasswordHash(normalized);

            if (storedHash == null) {
                lockout.recordFailure(normalized);
                System.err.println("[AuthManager] User '" + normalized + "' not found or password not set");
                return VerificationResult.USER_NOT_FOUND;
            }

            boolean matches = Pbkdf2PasswordHasher.verifyPassword(storedHash, enteredPassword);

            if (matches) {
                lockout.recordSuccess(normalized);
                System.out.println("[AuthManager] Password verified for user: " + normalized);
                return VerificationResult.SUCCESS;
            }

            lockout.recordFailure(normalized);
            System.err.println("[AuthManager] Password verification FAILED for user: " + normalized);
            return VerificationResult.FAILED;

        } catch (Exception e) {
            lockout.recordFailure(normalized);
            System.err.println("[AuthManager] Verification error: " + e.getMessage());
            e.printStackTrace();
            return VerificationResult.FAILED;
        }
    }

    /** @deprecated Prefer {@link #verifyMasterPassword(String, String)} for lockout-aware checks. */
    public boolean verifyMasterPasswordLegacy(String username, String enteredPassword) {
        return verifyMasterPassword(username, enteredPassword) == VerificationResult.SUCCESS;
    }

    public void setMasterPassword(String username, String masterPassword) throws Exception {
        String passwordHash = Pbkdf2PasswordHasher.hashPassword(masterPassword);
        DatabaseManager.getInstance().saveMasterPassword(username, passwordHash);
        SessionManager.getInstance().invalidateAllSessions();
        lockout.recordSuccess(username);
        System.out.println("[AuthManager] Master password set for user: " + username + " (PBKDF2)");
    }

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

    public boolean passwordNeedsRehashing(String username) {
        try {
            String storedHash = DatabaseManager.getInstance().loadPasswordHash(username);
            if (storedHash == null) {
                return false;
            }
            return Pbkdf2PasswordHasher.needsRehash(storedHash);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Records a failed recovery-word or export-key attempt for lockout tracking.
     */
    public void recordAuthenticationFailure(String username) {
        lockout.recordFailure(username);
    }

    public boolean isLockedOut(String username) {
        return lockout.isLocked(username);
    }

    public String lockoutMessage(String username) {
        return lockout.lockoutMessage(username);
    }

    public String failureMessage(String username) {
        return lockout.failureMessage(username);
    }
}
