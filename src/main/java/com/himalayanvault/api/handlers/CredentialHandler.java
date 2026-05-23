package com.himalayanvault.api.handlers;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKey;

import com.himalayanvault.api.dto.CredentialDeleteRequest;
import com.himalayanvault.api.dto.CredentialRequest;
import com.himalayanvault.api.dto.CredentialResponse;
import com.himalayanvault.api.dto.CredentialUpdateRequest;
import com.himalayanvault.api.util.JsonUtil;
import com.himalayanvault.db.DatabaseManager;
import com.himalayanvault.models.Credential;
import com.himalayanvault.security.SessionManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * CredentialHandler — handles credential-related endpoints:
 * GET /credentials?site=
 * POST /save
 * POST /delete
 * POST /update
 * GET /credential/{id}
 */
public class CredentialHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery() != null ? 
                          exchange.getRequestURI().getQuery() : "";

            if ("OPTIONS".equals(method)) {
                handleOptions(exchange);
            } else if (path.equals("/credentials") && "GET".equals(method)) {
                handleGetCredentials(exchange, query);
            } else if (path.equals("/save") && "POST".equals(method)) {
                handleSaveCredential(exchange);
            } else if (path.equals("/update") && "POST".equals(method)) {
                handleUpdateCredential(exchange);
            } else if (path.equals("/delete") && "POST".equals(method)) {
                handleDeleteCredential(exchange);
            } else if (path.startsWith("/credential/") && "GET".equals(method)) {
                handleGetCredentialById(exchange, path);
            } else {
                JsonUtil.sendNotFound(exchange);
            }
        } catch (Exception e) {
            System.err.println("[CredentialHandler] Error: " + e.getMessage());
            e.printStackTrace();
            try {
                JsonUtil.sendInternalError(exchange, e.getMessage());
            } catch (Exception ex) {
                System.err.println("[CredentialHandler] Error sending error response: " + ex.getMessage());
            }
        }
    }

    /**
     * GET /credentials?site=example.com — Get credentials for a site.
     */
    private void handleGetCredentials(HttpExchange exchange, String query) throws Exception {
        // Extract token from query or header
        String token = extractToken(exchange);
        if (token == null) {
            JsonUtil.sendUnauthorized(exchange);
            return;
        }

        SessionManager sessionManager = SessionManager.getInstance();
        String username = sessionManager.getUsernameFromToken(token);
        if (username == null) {
            JsonUtil.sendUnauthorized(exchange);
            return;
        }

        // Extract site parameter
        String siteUrl = "";
        if (query != null && !query.isEmpty()) {
            for (String param : query.split("&")) {
                if (param.startsWith("site=")) {
                    siteUrl = URLDecoder.decode(param.substring(5), StandardCharsets.UTF_8);
                    break;
                }
            }
        }

        try {
            DatabaseManager db = DatabaseManager.getInstance();
            List<Credential> credentials = new ArrayList<>();
            ResultSet rs;

            if (siteUrl.isEmpty()) {
                // Get all credentials
                rs = db.loadCredentialsForUser(username);
            } else {
                // Get credentials for specific site
                rs = db.loadCredentialsBySite(username, siteUrl);
            }

            while (rs != null && rs.next()) {
                Credential cred = resultSetToCredential(rs);
                credentials.add(cred);
            }
            if (rs != null) rs.close();

            CredentialResponse response = new CredentialResponse(true, "Credentials retrieved", credentials);
            JsonUtil.sendResponse(exchange, 200, response);
            System.out.println("[CredentialHandler] Retrieved " + credentials.size() + " credentials for user: " + username);

        } catch (Exception e) {
            JsonUtil.sendInternalError(exchange, e.getMessage());
        }
    }

    /**
     * POST /save — Save a new credential.
     */
    private void handleSaveCredential(HttpExchange exchange) throws Exception {
        String token = extractToken(exchange);
        if (token == null) {
            JsonUtil.sendUnauthorized(exchange);
            return;
        }

        SessionManager sessionManager = SessionManager.getInstance();
        String username = sessionManager.getUsernameFromToken(token);
        SecretKey encryptionKey = sessionManager.getEncryptionKey(token);
        
        if (username == null || encryptionKey == null) {
            JsonUtil.sendUnauthorized(exchange);
            return;
        }

        try {
            CredentialRequest request = JsonUtil.parseRequest(exchange, CredentialRequest.class);

            // Validate input
            if (request.siteUrl == null || request.siteUrl.trim().isEmpty()) {
                JsonUtil.sendBadRequest(exchange, "Site URL is required");
                return;
            }
            if (request.siteUsername == null || request.siteUsername.trim().isEmpty()) {
                JsonUtil.sendBadRequest(exchange, "Site username is required");
                return;
            }
            if (request.encryptedPassword == null || request.encryptedPassword.trim().isEmpty()) {
                JsonUtil.sendBadRequest(exchange, "Password is required");
                return;
            }

            DatabaseManager db = DatabaseManager.getInstance();
            long credId = db.saveCredential(
                username,
                request.siteUrl,
                request.siteName != null ? request.siteName : request.siteUrl,
                request.siteUsername,
                request.encryptedPassword,
                request.notes != null ? request.notes : ""
            );

            if (credId > 0) {
                CredentialResponse response = new CredentialResponse(true, "Credential saved with ID: " + credId);
                JsonUtil.sendResponse(exchange, 201, response);
                System.out.println("[CredentialHandler] Credential saved for user: " + username);
            } else {
                JsonUtil.sendInternalError(exchange, "Failed to save credential");
            }

        } catch (IllegalArgumentException e) {
            JsonUtil.sendBadRequest(exchange, e.getMessage());
        }
    }

    /**
     * POST /update — Update an existing credential.
     */
    private void handleUpdateCredential(HttpExchange exchange) throws Exception {
        String token = extractToken(exchange);
        if (token == null) {
            JsonUtil.sendUnauthorized(exchange);
            return;
        }

        SessionManager sessionManager = SessionManager.getInstance();
        String username = sessionManager.getUsernameFromToken(token);
        
        if (username == null) {
            JsonUtil.sendUnauthorized(exchange);
            return;
        }

        try {
            CredentialUpdateRequest request = JsonUtil.parseRequest(exchange, CredentialUpdateRequest.class);

            // Validate input
            if (request.credentialId <= 0) {
                JsonUtil.sendBadRequest(exchange, "Credential ID is required");
                return;
            }
            if (request.siteUrl == null || request.siteUrl.trim().isEmpty()) {
                JsonUtil.sendBadRequest(exchange, "Site URL is required");
                return;
            }

            DatabaseManager db = DatabaseManager.getInstance();
            boolean success = db.updateCredential(
                request.credentialId,
                username,
                request.siteUrl,
                request.siteName != null ? request.siteName : request.siteUrl,
                request.siteUsername,
                request.encryptedPassword,
                request.notes != null ? request.notes : ""
            );

            if (success) {
                CredentialResponse response = new CredentialResponse(true, "Credential updated");
                JsonUtil.sendResponse(exchange, 200, response);
            } else {
                JsonUtil.sendBadRequest(exchange, "Credential not found or not owned by user");
            }

        } catch (IllegalArgumentException e) {
            JsonUtil.sendBadRequest(exchange, e.getMessage());
        }
    }

    /**
     * POST /delete — Delete a credential.
     */
    private void handleDeleteCredential(HttpExchange exchange) throws Exception {
        String token = extractToken(exchange);
        if (token == null) {
            JsonUtil.sendUnauthorized(exchange);
            return;
        }

        SessionManager sessionManager = SessionManager.getInstance();
        String username = sessionManager.getUsernameFromToken(token);
        
        if (username == null) {
            JsonUtil.sendUnauthorized(exchange);
            return;
        }

        try {
            CredentialDeleteRequest request = JsonUtil.parseRequest(exchange, CredentialDeleteRequest.class);

            if (request.credentialId <= 0) {
                JsonUtil.sendBadRequest(exchange, "Credential ID is required");
                return;
            }

            DatabaseManager db = DatabaseManager.getInstance();
            boolean success = db.deleteCredential(request.credentialId, username);

            if (success) {
                CredentialResponse response = new CredentialResponse(true, "Credential deleted");
                JsonUtil.sendResponse(exchange, 200, response);
            } else {
                JsonUtil.sendBadRequest(exchange, "Credential not found or not owned by user");
            }

        } catch (IllegalArgumentException e) {
            JsonUtil.sendBadRequest(exchange, e.getMessage());
        }
    }

    /**
     * GET /credential/{id} — Get a specific credential by ID.
     */
    private void handleGetCredentialById(HttpExchange exchange, String path) throws Exception {
        String token = extractToken(exchange);
        if (token == null) {
            JsonUtil.sendUnauthorized(exchange);
            return;
        }

        SessionManager sessionManager = SessionManager.getInstance();
        String username = sessionManager.getUsernameFromToken(token);
        
        if (username == null) {
            JsonUtil.sendUnauthorized(exchange);
            return;
        }

        try {
            long credId = Long.parseLong(path.substring("/credential/".length()));
            DatabaseManager db = DatabaseManager.getInstance();
            ResultSet rs = db.loadCredentialById(credId, username);

            if (rs != null && rs.next()) {
                Credential cred = resultSetToCredential(rs);
                CredentialResponse response = new CredentialResponse(true, "Credential retrieved", cred);
                JsonUtil.sendResponse(exchange, 200, response);
                rs.close();
            } else {
                JsonUtil.sendBadRequest(exchange, "Credential not found");
            }

        } catch (NumberFormatException e) {
            JsonUtil.sendBadRequest(exchange, "Invalid credential ID");
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

    // ────────────────────────────────────────────────────────────────
    // Helper methods
    // ────────────────────────────────────────────────────────────────

    private String extractToken(HttpExchange exchange) {
        // Try to get token from Authorization header
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // Try to get token from query parameter
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) {
                    try {
                        return URLDecoder.decode(param.substring(6), StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
        }

        return null;
    }

    private Credential resultSetToCredential(ResultSet rs) throws Exception {
        return new Credential(
            rs.getLong("id"),
            rs.getString("owner_username"),
            rs.getString("site_url"),
            rs.getString("site_name"),
            rs.getString("site_username"),
            rs.getString("encrypted_password"),
            rs.getString("notes"),
            rs.getString("created_at"),
            rs.getString("updated_at")
        );
    }
}
