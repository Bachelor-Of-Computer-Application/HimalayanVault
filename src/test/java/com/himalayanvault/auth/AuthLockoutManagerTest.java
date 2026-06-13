package com.himalayanvault.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Auth lockout tests")
class AuthLockoutManagerTest {

    private AuthLockoutManager lockout;

    @BeforeEach
    void setUp() {
        lockout = AuthLockoutManager.getInstance();
        lockout.recordSuccess("lockout-test-user");
    }

    @Test
    @DisplayName("locks account after five failed attempts")
    void locksAfterFiveFailures() {
        String user = "lockout-test-user";

        for (int i = 0; i < AuthLockoutManager.MAX_ATTEMPTS - 1; i++) {
            assertEquals(0, lockout.recordFailure(user));
            assertFalse(lockout.isLocked(user));
        }

        assertTrue(lockout.recordFailure(user) > 0);
        assertTrue(lockout.isLocked(user));
        assertEquals(0, lockout.getRemainingAttempts(user));
    }

    @Test
    @DisplayName("successful auth clears failures")
    void successClearsFailures() {
        String user = "lockout-test-user";
        lockout.recordFailure(user);
        lockout.recordFailure(user);
        assertEquals(3, lockout.getRemainingAttempts(user));

        lockout.recordSuccess(user);
        assertEquals(AuthLockoutManager.MAX_ATTEMPTS, lockout.getRemainingAttempts(user));
        assertFalse(lockout.isLocked(user));
    }
}
