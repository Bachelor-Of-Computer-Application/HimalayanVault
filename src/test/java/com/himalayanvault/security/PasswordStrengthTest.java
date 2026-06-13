package com.himalayanvault.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Password strength tests")
class PasswordStrengthTest {

    @Test
    @DisplayName("scores weak and strong passwords")
    void scoresPasswords() {
        assertEquals(PasswordStrength.Level.EMPTY, PasswordStrength.level(""));
        assertEquals(PasswordStrength.Level.WEAK, PasswordStrength.level("short1"));
        assertEquals(PasswordStrength.Level.STRONG, PasswordStrength.level("SecurePass1!"));
    }

    @Test
    @DisplayName("validates master password requirements")
    void masterRequirements() {
        assertFalse(PasswordStrength.meetsMasterPasswordRequirements("password"));
        assertTrue(PasswordStrength.meetsMasterPasswordRequirements("SecurePass1!"));
    }
}
