package com.himalayanvault.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Session Manager Tests")
class SessionManagerTest {

    @AfterEach
    void tearDown() throws Exception {
        SessionManager.getInstance().lock();
        Field instanceField = SessionManager.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    @Test
    @DisplayName("Expired inactivity invalidates token and locks vault")
    void inactivityExpiryInvalidatesSessionAndLocksVault() throws Exception {
        SessionManager manager = SessionManager.getInstance();
        String token = manager.createSession("alice", "SecurePass1!", "salt".getBytes());
        assertTrue(manager.isValidToken(token));

        SessionManager.SessionData session = sessions(manager).get(token);
        session.lastActivity = System.currentTimeMillis() - (16 * 60 * 1000);

        assertNull(manager.validateSession(token));
        assertFalse(manager.isValidToken(token));
        assertTrue(manager.isLocked());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, SessionManager.SessionData> sessions(SessionManager manager) throws Exception {
        Field sessionsField = SessionManager.class.getDeclaredField("sessions");
        sessionsField.setAccessible(true);
        return (Map<String, SessionManager.SessionData>) sessionsField.get(manager);
    }
}
