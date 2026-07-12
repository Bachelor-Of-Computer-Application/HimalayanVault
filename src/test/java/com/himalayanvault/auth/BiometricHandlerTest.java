package com.himalayanvault.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Biometric Handler Tests")
class BiometricHandlerTest {

    private String originalOsName;

    @AfterEach
    void restoreOsName() {
        if (originalOsName != null) {
            System.setProperty("os.name", originalOsName);
        }
    }

    @Test
    @DisplayName("Linux fingerprint auth succeeds when the command succeeds")
    void authenticateSucceedsWhenFingerprintCommandSucceeds() {
        originalOsName = System.getProperty("os.name");
        System.setProperty("os.name", "Linux");

        BiometricHandler handler = new BiometricHandler(new BiometricHandler.CommandRunner() {
            @Override
            public boolean commandExists(String command) {
                return "fprintd-verify".equals(command);
            }

            @Override
            public BiometricHandler.CommandResult run(String... command) {
                return new BiometricHandler.CommandResult(0, "fingerprint verified");
            }
        });

        assertTrue(handler.isAvailable());
        assertTrue(handler.authenticate());
    }

    @Test
    @DisplayName("Linux fingerprint auth fails when the command is missing")
    void authenticateFailsWhenFingerprintCommandMissing() {
        originalOsName = System.getProperty("os.name");
        System.setProperty("os.name", "Linux");

        BiometricHandler handler = new BiometricHandler(new BiometricHandler.CommandRunner() {
            @Override
            public boolean commandExists(String command) {
                return false;
            }

            @Override
            public BiometricHandler.CommandResult run(String... command) {
                return new BiometricHandler.CommandResult(1, "missing");
            }
        });

        assertFalse(handler.isAvailable());
        assertFalse(handler.authenticate());
    }
}