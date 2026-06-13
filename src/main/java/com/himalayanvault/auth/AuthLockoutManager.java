package com.himalayanvault.auth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks failed authentication attempts and enforces temporary lockouts.
 * Shared by desktop UI, API, recovery, and vault import/export flows.
 */
public final class AuthLockoutManager {

    public static final int MAX_ATTEMPTS = 5;
    public static final int LOCKOUT_SECONDS = 30;

    private static final AuthLockoutManager INSTANCE = new AuthLockoutManager();

    private final Map<String, AttemptState> attemptsByUser = new ConcurrentHashMap<>();

    private AuthLockoutManager() {
    }

    public static AuthLockoutManager getInstance() {
        return INSTANCE;
    }

    public boolean isLocked(String username) {
        return getRemainingLockoutSeconds(username) > 0;
    }

    public long getRemainingLockoutSeconds(String username) {
        AttemptState state = stateFor(username);
        if (state == null || state.lockoutUntilMs <= 0) {
            return 0;
        }
        long remainingMs = state.lockoutUntilMs - System.currentTimeMillis();
        if (remainingMs <= 0) {
            state.failedAttempts = 0;
            state.lockoutUntilMs = 0;
            return 0;
        }
        return (remainingMs + 999) / 1000;
    }

    public int getFailedAttempts(String username) {
        AttemptState state = stateFor(username);
        if (state == null || isLocked(username)) {
            return 0;
        }
        return state.failedAttempts;
    }

    public int getRemainingAttempts(String username) {
        if (isLocked(username)) {
            return 0;
        }
        return Math.max(0, MAX_ATTEMPTS - getFailedAttempts(username));
    }

    /**
     * Record a failed password, recovery-word, or export-key attempt.
     *
     * @return seconds of lockout if triggered, otherwise 0
     */
    public long recordFailure(String username) {
        if (username == null || username.isBlank()) {
            return 0;
        }

        String key = normalize(username);
        AttemptState state = attemptsByUser.computeIfAbsent(key, ignored -> new AttemptState());

        if (isLocked(key)) {
            return getRemainingLockoutSeconds(key);
        }

        state.failedAttempts++;
        if (state.failedAttempts >= MAX_ATTEMPTS) {
            state.lockoutUntilMs = System.currentTimeMillis() + (LOCKOUT_SECONDS * 1000L);
            System.out.println("[AuthLockoutManager] User locked out for " + LOCKOUT_SECONDS
                    + "s after " + MAX_ATTEMPTS + " failed attempts: " + key);
            return LOCKOUT_SECONDS;
        }

        return 0;
    }

    public void recordSuccess(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        attemptsByUser.remove(normalize(username));
    }

    public String lockoutMessage(String username) {
        long seconds = getRemainingLockoutSeconds(username);
        if (seconds <= 0) {
            return "";
        }
        return "Too many failed attempts. Try again in " + seconds + " second"
                + (seconds == 1 ? "" : "s") + ".";
    }

    public String failureMessage(String username) {
        if (isLocked(username)) {
            return lockoutMessage(username);
        }
        int remaining = getRemainingAttempts(username);
        if (remaining <= 0) {
            return lockoutMessage(username);
        }
        return "Incorrect credentials. " + remaining + " attempt"
                + (remaining == 1 ? "" : "s") + " remaining.";
    }

    private AttemptState stateFor(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return attemptsByUser.get(normalize(username));
    }

    private static String normalize(String username) {
        return username.trim().toLowerCase();
    }

    private static final class AttemptState {
        private int failedAttempts;
        private long lockoutUntilMs;
    }
}
