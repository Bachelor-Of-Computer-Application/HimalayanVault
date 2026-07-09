package com.himalayanvault.api.handlers;

import java.sql.SQLException;
import java.util.List;

import com.himalayanvault.api.dto.SignupRequest;
import com.himalayanvault.api.dto.SignupResponse;
import com.himalayanvault.api.util.JsonUtil;
import com.himalayanvault.auth.AuthManager;
import com.himalayanvault.auth.RecoveryCodeManager;
import com.himalayanvault.db.DatabaseManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * SignupHandler — handles POST /signup for Chrome extension and API clients.
 */
public class SignupHandler implements HttpHandler {

    private final AuthManager authManager = new AuthManager();
    private final RecoveryCodeManager recoveryCodeManager = new RecoveryCodeManager();

    @Override
    public void handle(HttpExchange exchange) {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if ("OPTIONS".equals(method)) {
                handleOptions(exchange);
            } else if (path.equals("/signup") && "POST".equals(method)) {
                handleSignup(exchange);
            } else {
                JsonUtil.sendMethodNotAllowed(exchange);
            }
        } catch (Exception e) {
            System.err.println("[SignupHandler] Error: " + e.getMessage());
            e.printStackTrace();
            try {
                JsonUtil.sendInternalError(exchange, e.getMessage());
            } catch (Exception ex) {
                System.err.println("[SignupHandler] Error sending error response: " + ex.getMessage());
            }
        }
    }

    private void handleSignup(HttpExchange exchange) throws Exception {
        SignupRequest request = JsonUtil.parseRequest(exchange, SignupRequest.class);

        if (request.username == null || request.username.trim().isEmpty()) {
            JsonUtil.sendBadRequest(exchange, "Username is required");
            return;
        }
        if (request.password == null || request.password.trim().isEmpty()) {
            JsonUtil.sendBadRequest(exchange, "Password is required");
            return;
        }

        String username = request.username.trim();
        if (username.length() < 3) {
            JsonUtil.sendBadRequest(exchange, "Username must be at least 3 characters");
            return;
        }
        if (!meetsPasswordRequirements(request.password)) {
            JsonUtil.sendBadRequest(exchange,
                    "Password must be at least 12 characters with upper, lower, digit, and special character");
            return;
        }

        if (DatabaseManager.getInstance().isVaultInitialized(username)) {
            JsonUtil.sendResponse(exchange, 409, new SignupResponse(false,
                    "Username already exists. Please choose another username or log in.", null));
            return;
        }

        try {
            List<String> recoveryWords = recoveryCodeManager.generateMnemonic();
            DatabaseManager.getInstance().withTransaction(connection -> {
                if (DatabaseManager.getInstance().isVaultInitialized(username)) {
                    throw new SQLException("Username already exists");
                }

                authManager.setMasterPassword(connection, username, request.password);
                recoveryCodeManager.storeHashedMnemonic(connection, username, recoveryWords);
            });

            System.out.println("[SignupHandler] Vault created for user: " + username);
            SignupResponse response = new SignupResponse(true, "Signup successful", recoveryWords);
            JsonUtil.sendResponse(exchange, 201, response);
        } catch (SQLException e) {
            if (isDuplicateUsernameError(e)) {
                JsonUtil.sendResponse(exchange, 409, new SignupResponse(false,
                        "Username already exists. Please choose another username or log in.", null));
            } else {
                System.err.println("[SignupHandler] Vault creation failed: " + e.getMessage());
                JsonUtil.sendInternalError(exchange, "Failed to create vault: " + e.getMessage());
            }
        } catch (Exception e) {
            System.err.println("[SignupHandler] Vault creation failed: " + e.getMessage());
            JsonUtil.sendInternalError(exchange, "Failed to create vault: " + e.getMessage());
        }
    }

    private boolean isDuplicateUsernameError(SQLException e) {
        String message = e.getMessage();
        return message != null && (message.contains("Username already exists")
                || message.contains("UNIQUE constraint failed"));
    }

    private boolean meetsPasswordRequirements(String password) {
        return password.length() >= 12
                && password.matches(".*[A-Z].*")
                && password.matches(".*[a-z].*")
                && password.matches(".*[0-9].*")
                && password.matches(".*[^A-Za-z0-9].*");
    }

    private void handleOptions(HttpExchange exchange) throws Exception {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "chrome-extension://*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");
        exchange.sendResponseHeaders(204, -1);
    }
}
