package com.himalayanvault.api.handlers;

import com.himalayanvault.api.dto.LockRequest;
import com.himalayanvault.api.dto.LoginRequest;
import com.himalayanvault.api.dto.LoginResponse;
import com.himalayanvault.api.util.JsonUtil;
import com.himalayanvault.auth.AuthManager;
import com.himalayanvault.security.CredentialKeyDerivation;
import com.himalayanvault.security.SessionManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * AuthHandler — handles POST /login and POST /lock endpoints.
 * Validates master password and creates secure session tokens.
 */
public class AuthHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if (path.equals("/login") && "POST".equals(method)) {
                handleLogin(exchange);
            } else if (path.equals("/lock") && "POST".equals(method)) {
                handleLock(exchange);
            } else if ("OPTIONS".equals(method)) {
                handleOptions(exchange);
            } else {
                JsonUtil.sendMethodNotAllowed(exchange);
            }
        } catch (Exception e) {
            System.err.println("[AuthHandler] Error: " + e.getMessage());
            e.printStackTrace();
            try {
                JsonUtil.sendInternalError(exchange, e.getMessage());
            } catch (Exception ex) {
                System.err.println("[AuthHandler] Error sending error response: " + ex.getMessage());
            }
        }
    }

    /**
     * POST /login — Validate master password and return session token.
     */
    private void handleLogin(HttpExchange exchange) throws Exception {
        try {
            LoginRequest request = JsonUtil.parseRequest(exchange, LoginRequest.class);

            // Validate input
            if (request.username == null || request.username.trim().isEmpty()) {
                JsonUtil.sendBadRequest(exchange, "Username is required");
                return;
            }
            if (request.password == null || request.password.trim().isEmpty()) {
                JsonUtil.sendBadRequest(exchange, "Password is required");
                return;
            }

            // Verify master password
            AuthManager authManager = new AuthManager();
            if (!authManager.verifyMasterPassword(request.username, request.password)) {
                System.out.println("[AuthHandler] Login failed for user: " + request.username);
                LoginResponse response = new LoginResponse(false, null, "Invalid username or password");
                JsonUtil.sendResponse(exchange, 401, response);
                return;
            }

            // Create session with random salt for key derivation (distinct from password hash)
            String token = SessionManager.getInstance().createSession(
                    request.username,
                    request.password,
                    CredentialKeyDerivation.saltForUser(request.username));

            System.out.println("[AuthHandler] Login successful for user: " + request.username);
            LoginResponse response = new LoginResponse(true, token, "Login successful");
            JsonUtil.sendResponse(exchange, 200, response);

        } catch (IllegalArgumentException e) {
            JsonUtil.sendBadRequest(exchange, e.getMessage());
        }
    }

    /**
     * POST /lock — Invalidate session token (logout).
     */
    private void handleLock(HttpExchange exchange) throws Exception {
        try {
            LockRequest request = JsonUtil.parseRequest(exchange, LockRequest.class);

            if (request.token == null || request.token.trim().isEmpty()) {
                JsonUtil.sendBadRequest(exchange, "Token is required");
                return;
            }

            SessionManager sessionManager = SessionManager.getInstance();
            String username = sessionManager.getUsernameFromToken(request.token);
            
            if (username == null) {
                JsonUtil.sendUnauthorized(exchange);
                return;
            }

            sessionManager.invalidateSession(request.token);
            System.out.println("[AuthHandler] User locked: " + username);

            ApiResponse response = new ApiResponse(true, "Locked successfully");
            JsonUtil.sendResponse(exchange, 200, response);

        } catch (IllegalArgumentException e) {
            JsonUtil.sendBadRequest(exchange, e.getMessage());
        }
    }

    /**
     * OPTIONS — Handle CORS preflight requests.
     */
    private void handleOptions(HttpExchange exchange) throws Exception {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "chrome-extension://*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");
        exchange.sendResponseHeaders(204, -1);
    }

    /**
     * Generic API response wrapper.
     */
    public static class ApiResponse {
        public boolean success;
        public String message;

        public ApiResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}
