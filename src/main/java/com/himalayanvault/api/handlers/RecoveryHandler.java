package com.himalayanvault.api.handlers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.himalayanvault.api.util.JsonUtil;
import com.himalayanvault.auth.AuthLockoutManager;
import com.himalayanvault.auth.AuthManager;
import com.himalayanvault.auth.RecoveryCodeManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * RecoveryHandler — handles recovery-word verification and password reset requests.
 * The extension uses this handler to mirror the desktop recovery flow.
 */
public class RecoveryHandler implements HttpHandler {

    private static final long VERIFICATION_TTL_MS = TimeUnit.MINUTES.toMillis(10);
    private static final Map<String, Long> VERIFIED_USERS = new ConcurrentHashMap<>();

    private final RecoveryCodeManager recoveryCodeManager = new RecoveryCodeManager();
    private final AuthManager authManager = new AuthManager();

    @Override
    public void handle(HttpExchange exchange) {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if ("OPTIONS".equals(method)) {
                handleOptions(exchange);
                return;
            }

            if ("POST".equals(method) && path.equals("/recovery/verify")) {
                handleVerify(exchange);
            } else if ("POST".equals(method) && path.equals("/recovery/reset")) {
                handleReset(exchange);
            } else {
                JsonUtil.sendMethodNotAllowed(exchange);
            }
        } catch (Exception e) {
            System.err.println("[RecoveryHandler] Error: " + e.getMessage());
            e.printStackTrace();
            try {
                JsonUtil.sendInternalError(exchange, e.getMessage());
            } catch (Exception ignored) {
                System.err.println("[RecoveryHandler] Error sending error response: " + ignored.getMessage());
            }
        }
    }

    private void handleVerify(HttpExchange exchange) throws Exception {
        RecoveryVerifyRequest request = JsonUtil.parseRequest(exchange, RecoveryVerifyRequest.class);

        if (request.username == null || request.username.trim().isEmpty()) {
            JsonUtil.sendBadRequest(exchange, "Username is required");
            return;
        }
        if (request.words == null || request.words.size() != 16) {
            JsonUtil.sendBadRequest(exchange, "Exactly 16 recovery words are required");
            return;
        }

        String username = request.username.trim();

        if (authManager.isLockedOut(username)) {
            JsonUtil.sendTooManyRequests(exchange, authManager.lockoutMessage(username));
            return;
        }

        boolean verified = recoveryCodeManager.verifyMnemonic(username, request.words);
        if (!verified) {
            authManager.recordAuthenticationFailure(username);
            if (authManager.isLockedOut(username)) {
                JsonUtil.sendTooManyRequests(exchange, authManager.lockoutMessage(username));
            } else {
                JsonUtil.sendUnauthorized(exchange);
            }
            return;
        }

        AuthLockoutManager.getInstance().recordSuccess(username);
        VERIFIED_USERS.put(username, System.currentTimeMillis() + VERIFICATION_TTL_MS);
        JsonUtil.sendResponse(exchange, 200, new RecoveryResponse(true, "Recovery words verified"));
    }

    private void handleReset(HttpExchange exchange) throws Exception {
        purgeExpiredVerifications();

        RecoveryResetRequest request = JsonUtil.parseRequest(exchange, RecoveryResetRequest.class);

        if (request.username == null || request.username.trim().isEmpty()) {
            JsonUtil.sendBadRequest(exchange, "Username is required");
            return;
        }
        if (request.newPassword == null || request.newPassword.trim().isEmpty()) {
            JsonUtil.sendBadRequest(exchange, "New password is required");
            return;
        }

        String username = request.username.trim();
        if (!isVerified(username)) {
            JsonUtil.sendUnauthorized(exchange);
            return;
        }

        boolean success = authManager.resetPasswordWithRecovery(username, request.newPassword);
        if (!success) {
            JsonUtil.sendInternalError(exchange, "Failed to reset password");
            return;
        }

        VERIFIED_USERS.remove(username);
        JsonUtil.sendResponse(exchange, 200, new RecoveryResponse(true, "Password reset successful"));
    }

    private void handleOptions(HttpExchange exchange) throws Exception {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "chrome-extension://*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");
        exchange.sendResponseHeaders(204, -1);
    }

    private boolean isVerified(String username) {
        Long expiresAt = VERIFIED_USERS.get(username);
        if (expiresAt == null) {
            return false;
        }
        if (System.currentTimeMillis() > expiresAt) {
            VERIFIED_USERS.remove(username);
            return false;
        }
        return true;
    }

    private void purgeExpiredVerifications() {
        long now = System.currentTimeMillis();
        VERIFIED_USERS.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    public static class RecoveryVerifyRequest {
        public String username;
        public List<String> words;
    }

    public static class RecoveryResetRequest {
        public String username;
        public String newPassword;
    }

    public static class RecoveryResponse {
        public boolean success;
        public String message;

        public RecoveryResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}