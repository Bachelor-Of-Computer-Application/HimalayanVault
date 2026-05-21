package com.himalayanvault.auth;

/**
 * BiometricHandler — integrates OS-level biometric authentication.
 *
 * Windows Hello  : via JNA calling WinBio API
 * macOS Touch ID : via JNA calling LocalAuthentication framework
 * Linux PAM      : via libpam-java
 *
 * This stub always returns false; replace the body of authenticate()
 * with real JNA bindings when implementing.
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
        // TODO: check if the OS has enrolled biometrics
        return false;
    }

    // ── OS-specific stubs ─────────────────────────────────────────────

    private boolean authenticateWindowsHello() {
        // TODO: JNA call to WinBio.dll
        // WinBioVerify(sessionHandle, &identity, subFactor, &match, ...)
        System.out.println("[Biometric] Windows Hello prompt triggered (stub)");
        return false;
    }

    private boolean authenticateTouchId() {
        // TODO: JNA call to macOS LocalAuthentication
        // LAContext.evaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics)
        System.out.println("[Biometric] macOS Touch ID prompt triggered (stub)");
        return false;
    }

    private boolean authenticatePam() {
        // TODO: libpam-java call
        System.out.println("[Biometric] Linux PAM prompt triggered (stub)");
        return false;
    }
}
