package com.himalayanvault.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKey;

/**
 * SessionManager — manages API session tokens and timeouts with enhanced security.
 * 
 * Features:
 * - Auto-lock after 15 minutes of inactivity
 * - Session binding to device identifier (prevents token replay)
 * - Session invalidation on master password change
 * - Secure token generation and cleanup
 * 
 * Thread-safe implementation using ConcurrentHashMap.
 */
public class SessionManager {

    private static final long INACTIVITY_TIMEOUT_MS = 15 * 60 * 1000;  // 15 minutes
    private static final long SESSION_MAX_AGE_MS = 24 * 60 * 60 * 1000; // 24 hours
    private static final int TOKEN_SIZE = 32; // bytes

    private static SessionManager instance;
    private final Map<String, SessionData> sessions = new ConcurrentHashMap<>();
    private final Object lock = new Object();
    
    private String deviceId;
    private boolean isLocked = true;

    private SessionManager() {
        this.deviceId = generateDeviceId();
        // Start a background thread to clean up expired sessions
        Thread cleanupThread = new Thread(this::cleanupExpiredSessions);
        cleanupThread.setName("SessionCleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    /**
     * Create a new session for a user.
     *
     * @param username        the username
     * @param masterPassword  the master password (for key derivation)
     * @param masterSalt      the salt from vault (for key derivation)
     * @return the session token
     */
    public String createSession(String username, String masterPassword, byte[] masterSalt) {
        String token = generateToken();
        SecretKey encryptionKey = EncryptionUtil.deriveKeyFromPassword(masterPassword, masterSalt);
        SessionData session = new SessionData(username, encryptionKey, this.deviceId);
        sessions.put(token, session);
        this.isLocked = false;
        System.out.println("[SessionManager] Session created for user: " + username + ", device ID: " + deviceId);
        return token;
    }

    /**
     * Create a new session for a user from an already derived encryption key.
     * Used when biometric authentication reuses a vault key that was cached
     * after a successful master-password unlock.
     */
    public String createSession(String username, SecretKey encryptionKey) {
        String token = generateToken();
        SessionData session = new SessionData(username, encryptionKey, this.deviceId);
        sessions.put(token, session);
        this.isLocked = false;
        System.out.println("[SessionManager] Session created for user: " + username + ", device ID: " + deviceId);
        return token;
    }

    /**
     * Validate a session token and refresh its timeout.
     * Checks: token exists, not expired, not locked, device matches.
     *
     * @param token the session token
     * @return the SessionData if valid, or null if invalid/expired
     */
    public SessionData validateSession(String token) {
        if (isLocked) {
            System.err.println("[SessionManager] Session is locked (auto-lock triggered)");
            return null;
        }

        SessionData session = sessions.get(token);
        if (session == null) {
            System.err.println("[SessionManager] Session not found: " + token.substring(0, 8) + "...");
            return null;
        }

        long now = System.currentTimeMillis();
        long age = now - session.createdAt;
        long inactivity = now - session.lastActivity;

        // Check max session age
        if (age > SESSION_MAX_AGE_MS) {
            sessions.remove(token);
            System.out.println("[SessionManager] Session expired (max age exceeded): " + token.substring(0, 8) + "...");
            return null;
        }

        // Check inactivity timeout (auto-lock)
        if (inactivity > INACTIVITY_TIMEOUT_MS) {
            sessions.remove(token);
            isLocked = true;
            System.out.println("[SessionManager] Session auto-locked due to 15 min inactivity");
            return null;
        }

        // Check device binding (prevent token replay from different machines)
        if (!session.deviceId.equals(this.deviceId)) {
            sessions.remove(token);
            System.out.println("[SessionManager] Session rejected: device mismatch (possible token replay)");
            return null;
        }

        // Refresh last activity time
        session.refreshActivity();
        return session;
    }

    /**
     * Invalidate a single session (user logout).
     *
     * @param token the session token
     */
    public void invalidateSession(String token) {
        SessionData session = sessions.remove(token);
        if (session != null) {
            System.out.println("[SessionManager] Session invalidated for user: " + session.username);
        }
    }

    /**
     * Invalidate all sessions (used when master password changes).
     * Prevents old tokens from remaining valid after password rotation.
     */
    public void invalidateAllSessions() {
        synchronized (lock) {
            int count = sessions.size();
            sessions.clear();
            isLocked = true;
            System.out.println("[SessionManager] All " + count + " sessions invalidated (master password changed)");
        }
    }

    /**
     * Lock the vault immediately (user requested or app closing).
     */
    public void lock() {
        synchronized (lock) {
            sessions.clear();
            isLocked = true;
        }
        System.out.println("[SessionManager] Vault locked");
    }

    /**
     * Check if vault is currently locked.
     *
     * @return true if locked
     */
    public boolean isLocked() {
        return isLocked;
    }

    /**
     * Get the username from a valid session token.
     *
     * @param token the session token
     * @return the username, or null if session invalid
     */
    public String getUsernameFromToken(String token) {
        SessionData session = validateSession(token);
        return session != null ? session.username : null;
    }

    /**
     * Get the encryption key for a valid session.
     *
     * @param token the session token
     * @return the encryption key, or null if session invalid
     */
    public SecretKey getEncryptionKey(String token) {
        SessionData session = validateSession(token);
        return session != null ? session.encryptionKey : null;
    }

    /**
     * Check if a token is valid.
     *
     * @param token the session token
     * @return true if valid and not expired
     */
    public boolean isValidToken(String token) {
        return validateSession(token) != null;
    }

    /**
     * Get the number of active sessions.
     *
     * @return count of active sessions
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    // ────────────────────────────────────────────────────────────────
    // Private helpers
    // ────────────────────────────────────────────────────────────────

    private String generateToken() {
        byte[] randomBytes = new byte[TOKEN_SIZE];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Generate a device ID based on system properties.
     * Used to bind sessions to a specific device (prevents token replay).
     */
    private String generateDeviceId() {
        try {
            String osName = System.getProperty("os.name");
            String osArch = System.getProperty("os.arch");
            String javaVersion = System.getProperty("java.version");
            String deviceInfo = osName + "|" + osArch + "|" + javaVersion;

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(deviceInfo.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash).substring(0, 16);
        } catch (Exception e) {
            // Fallback to random ID
            return Base64.getEncoder().encodeToString(
                new byte[]{(byte) new SecureRandom().nextInt()}
            ).substring(0, 16);
        }
    }

    private void cleanupExpiredSessions() {
        while (true) {
            try {
                Thread.sleep(60_000); // Check every 60 seconds

                long now = System.currentTimeMillis();
                sessions.entrySet().removeIf(entry -> {
                    SessionData sd = entry.getValue();
                    long age = now - sd.createdAt;
                    long inactivity = now - sd.lastActivity;
                    return (age > SESSION_MAX_AGE_MS) || (inactivity > INACTIVITY_TIMEOUT_MS);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // ────────────────────────────────────────────────────────────────
    // SessionData inner class
    // ────────────────────────────────────────────────────────────────

    public static class SessionData {
        public final String username;
        public final SecretKey encryptionKey;
        public final String deviceId;
        public final long createdAt;
        public long lastActivity;

        SessionData(String username, SecretKey encryptionKey, String deviceId) {
            this.username = username;
            this.encryptionKey = encryptionKey;
            this.deviceId = deviceId;
            this.createdAt = System.currentTimeMillis();
            this.lastActivity = createdAt;
        }

        void refreshActivity() {
            this.lastActivity = System.currentTimeMillis();
        }
    }
}
