package com.himalayanvault.api.handlers;

import com.himalayanvault.api.dto.PasswordGenerateRequest;
import com.himalayanvault.api.dto.PasswordGenerateResponse;
import com.himalayanvault.api.util.JsonUtil;
import com.himalayanvault.security.EncryptionUtil;
import com.himalayanvault.security.SessionManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * PasswordHandler — handles POST /generate-password endpoint for password generation.
 */
public class PasswordHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) {
        try {
            String method = exchange.getRequestMethod();

            if ("OPTIONS".equals(method)) {
                handleOptions(exchange);
                return;
            }

            if ("POST".equals(method)) {
                // Verify token
                String token = extractToken(exchange);
                SessionManager sessionManager = SessionManager.getInstance();
                if (token == null || !sessionManager.isValidToken(token)) {
                    JsonUtil.sendUnauthorized(exchange);
                    return;
                }

                // Parse request
                PasswordGenerateRequest request = JsonUtil.parseRequest(exchange, PasswordGenerateRequest.class);

                // Validate length
                if (request.length < 8 || request.length > 128) {
                    JsonUtil.sendBadRequest(exchange, "Password length must be between 8 and 128");
                    return;
                }

                // Generate password
                String password = EncryptionUtil.generatePassword(
                    request.length,
                    request.useUppercase,
                    request.useLowercase,
                    request.useNumbers,
                    request.useSpecialChars
                );

                PasswordGenerateResponse response = new PasswordGenerateResponse(true, password, "Password generated");
                JsonUtil.sendResponse(exchange, 200, response);
                System.out.println("[PasswordHandler] Password generated successfully");

            } else {
                JsonUtil.sendMethodNotAllowed(exchange);
            }
        } catch (IllegalArgumentException e) {
            try {
                JsonUtil.sendBadRequest(exchange, e.getMessage());
            } catch (Exception ex) {
                System.err.println("[PasswordHandler] Error sending response: " + ex.getMessage());
            }
        } catch (Exception e) {
            System.err.println("[PasswordHandler] Error: " + e.getMessage());
            try {
                JsonUtil.sendInternalError(exchange, e.getMessage());
            } catch (Exception ex) {
                System.err.println("[PasswordHandler] Error sending error response: " + ex.getMessage());
            }
        }
    }

    private void handleOptions(HttpExchange exchange) throws Exception {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "chrome-extension://*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.sendResponseHeaders(204, -1);
    }

    private String extractToken(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
