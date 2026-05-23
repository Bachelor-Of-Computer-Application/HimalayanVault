package com.himalayanvault.security;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKey;

/**
 * SessionManager — manages API session tokens and timeouts.
 * Thread-safe implementation using ConcurrentHashMap.
 * Session tokens are valid for 30 minutes of inactivity.
 */
public class SessionManager {

    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000;  // 30 minutes
    private static final int TOKEN_SIZE = 32; // bytes

    private static SessionManager instance;
    private final Map<String, SessionData> sessions = new ConcurrentHashMap<>();
    private final Object lock = new Object();

    private SessionManager() {
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
        SessionData session = new SessionData(username, encryptionKey);
        sessions.put(token, session);
        System.out.println("[SessionManager] Session created for user: " + username + ", token: " + token.substring(0, 8) + "...");
        return token;
    }

    /**
     * Validate a session token and refresh its timeout.
     *
     * @param token the session token
     * @return the SessionData if valid, or null if invalid/expired
     */
    public SessionData validateSession(String token) {
        SessionData session = sessions.get(token);
        if (session == null) {
            System.err.println("[SessionManager] Session not found: " + token.substring(0, 8) + "...");
            return null;
        }

        // Check timeout
        if (System.currentTimeMillis() - session.lastActivity > SESSION_TIMEOUT_MS) {
            sessions.remove(token);
            System.out.println("[SessionManager] Session expired: " + token.substring(0, 8) + "...");
            return null;
        }

        // Refresh last activity time
        session.refreshActivity();
        return session;
    }

    /**
     * Invalidate (logout) a session token.
     *
     * @param token the session token
     */
    public void invalidateSession(String token) {
        SessionData session = sessions.remove(token);
        if (session != null) {
            System.out.println("[SessionManager] Session invalidated for user: " + session.username + 
                             ", token: " + token.substring(0, 8) + "...");
        }
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

    // ────────────────────────────────────────────────────────────────
    // Private helpers
    // ────────────────────────────────────────────────────────────────

    private String generateToken() {
        byte[] randomBytes = new byte[TOKEN_SIZE];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private void cleanupExpiredSessions() {
        while (true) {
            try {
                Thread.sleep(60_000); // Check every 60 seconds

                long now = System.currentTimeMillis();
                sessions.entrySet().removeIf(entry -> 
                    now - entry.getValue().lastActivity > SESSION_TIMEOUT_MS
                );
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
        public long lastActivity;

        SessionData(String username, SecretKey encryptionKey) {
            this.username = username;
            this.encryptionKey = encryptionKey;
            this.lastActivity = System.currentTimeMillis();
        }

        void refreshActivity() {
            this.lastActivity = System.currentTimeMillis();
        }
    }
}
