package com.himalayanvault.auth;

/**
 * BiometricHandler — integrates OS-level biometric authentication.
 *
 * Windows Hello  : via JNA calling WinBio API
 * macOS Touch ID : via JNA calling LocalAuthentication framework
 * Linux PAM      : via libpam-java
 *
 * This integration is currently disabled and always returns false.
 */
public class BiometricHandler {

    /**
     * Triggers the OS biometric prompt and blocks until the user
     * responds or the prompt times out.
     *
     * @return true if biometric authentication succeeded
     */
    public boolean authenticate() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            return authenticateWindowsHello();
        } else if (os.contains("mac")) {
            return authenticateTouchId();
        } else {
            return authenticatePam();
        }
    }

    public boolean isAvailable() {
        return false;
    }

    // ── OS-specific stubs ─────────────────────────────────────────────

    private boolean authenticateWindowsHello() {
        System.out.println("[Biometric] Windows Hello is not enabled in this build");
        return false;
    }

    private boolean authenticateTouchId() {
        System.out.println("[Biometric] macOS Touch ID is not enabled in this build");
        return false;
    }

    private boolean authenticatePam() {
        System.out.println("[Biometric] Linux PAM biometrics are not enabled in this build");
        return false;
    }
}
