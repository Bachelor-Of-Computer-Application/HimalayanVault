package com.himalayanvault.auth;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * BiometricHandler — integrates OS-level biometric authentication.
 *
 * Windows Hello  : via JNA calling WinBio API
 * macOS Touch ID : via JNA calling LocalAuthentication framework
 * Linux PAM      : via libpam-java / fprintd
 *
 * Linux builds can use fprintd when it is installed and the user has enrolled
 * fingerprints. Other platforms remain stubs until native bindings are added.
 */
public class BiometricHandler {

    interface CommandRunner {
        boolean commandExists(String command);

        CommandResult run(String... command);
    }

    record CommandResult(int exitCode, String output) {}

    private static final class ProcessCommandRunner implements CommandRunner {
        @Override
        public boolean commandExists(String command) {
            return run("sh", "-lc", "command -v " + command + " >/dev/null 2>&1").exitCode() == 0;
        }

        @Override
        public CommandResult run(String... command) {
            try {
                Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (output.length() > 0) {
                            output.append(System.lineSeparator());
                        }
                        output.append(line);
                    }
                }
                int exitCode = process.waitFor();
                return new CommandResult(exitCode, output.toString());
            } catch (java.io.IOException e) {
                return new CommandResult(1, e.getMessage() == null ? "" : e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new CommandResult(1, e.getMessage() == null ? "" : e.getMessage());
            }
        }
    }

    private final CommandRunner commandRunner;

    public BiometricHandler() {
        this(new ProcessCommandRunner());
    }

    BiometricHandler(CommandRunner commandRunner) {
        this.commandRunner = commandRunner;
    }

    /**
     * Triggers the OS biometric prompt and blocks until the user
     * responds or the prompt times out.
     *
     * @return true if biometric authentication succeeded
     */
    public boolean authenticate() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("linux")) {
            return authenticatePam();
        } else if (os.contains("win")) {
            return authenticateWindowsHello();
        } else if (os.contains("mac")) {
            return authenticateTouchId();
        } else {
            System.out.println("[Biometric] Unsupported operating system for biometric unlock: " + os);
            return false;
        }
    }

    public boolean isAvailable() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("linux")) {
            return commandRunner.commandExists("fprintd-verify");
        }
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
        if (!commandRunner.commandExists("fprintd-verify")) {
            System.out.println("[Biometric] fprintd-verify is not installed or not on PATH");
            return false;
        }

        CommandResult result = commandRunner.run("fprintd-verify");
        if (result.exitCode() == 0) {
            return true;
        }

        String output = result.output();
        if (!output.isBlank()) {
            System.out.println("[Biometric] Fingerprint verification failed: " + output);
        } else {
            System.out.println("[Biometric] Fingerprint verification failed");
        }
        return false;
    }
}
