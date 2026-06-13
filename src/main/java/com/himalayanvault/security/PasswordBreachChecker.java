package com.himalayanvault.security;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Locale;

/**
 * Checks passwords against Have I Been Pwned's k-anonymity password range API.
 */
public final class PasswordBreachChecker {

    private static final String RANGE_API = "https://api.pwnedpasswords.com/range/";
    private static final Duration TIMEOUT = Duration.ofSeconds(6);

    private final HttpClient httpClient;

    public PasswordBreachChecker() {
        this(HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    PasswordBreachChecker(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public BreachResult check(String password) throws IOException, InterruptedException {
        if (password == null || password.isBlank()) {
            return BreachResult.notChecked();
        }

        String sha1 = sha1Hex(password);
        String prefix = sha1.substring(0, 5);
        String suffix = sha1.substring(5);

        HttpRequest request = HttpRequest.newBuilder(URI.create(RANGE_API + prefix))
                .timeout(TIMEOUT)
                .header("User-Agent", "HimalayanVault")
                .header("Add-Padding", "true")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HIBP responded with HTTP " + response.statusCode());
        }

        int count = findBreachCount(response.body(), suffix);
        return count > 0 ? BreachResult.found(count) : BreachResult.notFound();
    }

    static int findBreachCount(String rangeResponse, String suffix) {
        if (rangeResponse == null || suffix == null || suffix.isBlank()) {
            return 0;
        }

        String normalizedSuffix = suffix.toUpperCase(Locale.ROOT);
        String[] lines = rangeResponse.split("\\R");
        for (String line : lines) {
            String[] parts = line.trim().split(":", 2);
            if (parts.length == 2 && parts[0].equalsIgnoreCase(normalizedSuffix)) {
                try {
                    return Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    static String sha1Hex(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format("%02X", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is not available", e);
        }
    }

    public record BreachResult(Status status, int count) {
        public static BreachResult found(int count) {
            return new BreachResult(Status.FOUND, count);
        }

        public static BreachResult notFound() {
            return new BreachResult(Status.NOT_FOUND, 0);
        }

        public static BreachResult notChecked() {
            return new BreachResult(Status.NOT_CHECKED, 0);
        }
    }

    public enum Status {
        FOUND, NOT_FOUND, NOT_CHECKED
    }
}
