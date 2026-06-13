package com.himalayanvault.security;

/**
 * Shared password strength scoring for master passwords and vault credentials.
 */
public final class PasswordStrength {

    public enum Level {
        EMPTY, WEAK, FAIR, GOOD, STRONG
    }

    public static final String COLOR_EMPTY = "#DDE3EE";
    public static final String COLOR_WEAK = "#E24B4A";
    public static final String COLOR_FAIR = "#EF9F27";
    public static final String COLOR_GOOD = "#028090";
    public static final String COLOR_STRONG = "#2E6B0A";

    private PasswordStrength() {
    }

    public static int score(String password) {
        if (password == null || password.isEmpty()) {
            return 0;
        }

        int score = 0;
        if (password.length() >= 8) {
            score++;
        }
        if (password.length() >= 12) {
            score++;
        }
        if (password.matches(".*[A-Z].*") && password.matches(".*[0-9].*")) {
            score++;
        }
        if (password.matches(".*[^A-Za-z0-9].*")) {
            score++;
        }
        return score;
    }

    public static Level level(String password) {
        if (password == null || password.isEmpty()) {
            return Level.EMPTY;
        }
        return switch (score(password)) {
            case 0, 1 -> Level.WEAK;
            case 2 -> Level.FAIR;
            case 3 -> Level.GOOD;
            default -> Level.STRONG;
        };
    }

    public static String label(Level level) {
        return switch (level) {
            case WEAK -> "Weak";
            case FAIR -> "Fair";
            case GOOD -> "Good";
            case STRONG -> "Strong";
            default -> "";
        };
    }

    public static String color(Level level) {
        return switch (level) {
            case WEAK -> COLOR_WEAK;
            case FAIR -> COLOR_FAIR;
            case GOOD -> COLOR_GOOD;
            case STRONG -> COLOR_STRONG;
            default -> COLOR_EMPTY;
        };
    }

    public static boolean meetsMasterPasswordRequirements(String password) {
        if (password == null) {
            return false;
        }
        return password.length() >= 12
                && password.matches(".*[A-Z].*")
                && password.matches(".*[a-z].*")
                && password.matches(".*[0-9].*")
                && password.matches(".*[^A-Za-z0-9].*");
    }

    public static boolean meetsCredentialPasswordRequirements(String password) {
        return password != null && !password.isBlank() && password.length() >= 4;
    }
}
