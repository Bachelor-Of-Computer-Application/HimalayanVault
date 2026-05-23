package com.himalayanvault.api.handlers;

import java.time.Instant;

import com.himalayanvault.api.dto.HealthResponse;
import com.himalayanvault.api.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * HealthHandler — handles GET /health endpoint for liveness checks.
 */
public class HealthHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) {
        try {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }

            if ("GET".equals(exchange.getRequestMethod())) {
                HealthResponse response = new HealthResponse(
                    true,
                    "API Server is running",
                    Instant.now().toString()
                );
                JsonUtil.sendResponse(exchange, 200, response);
                System.out.println("[HealthHandler] Health check OK");
            } else {
                JsonUtil.sendMethodNotAllowed(exchange);
            }
        } catch (Exception e) {
            System.err.println("[HealthHandler] Error: " + e.getMessage());
            try {
                JsonUtil.sendInternalError(exchange, e.getMessage());
            } catch (Exception ex) {
                System.err.println("[HealthHandler] Error sending error response: " + ex.getMessage());
            }
        }
    }

    private void handleOptions(HttpExchange exchange) throws Exception {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "chrome-extension://*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(204, -1);
    }
}
