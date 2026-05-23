package com.himalayanvault.api.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;

/**
 * JsonUtil — utility class for JSON serialization/deserialization and HTTP response writing.
 */
public class JsonUtil {

    private static final Gson gson = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    /**
     * Serialize an object to JSON string.
     *
     * @param obj the object to serialize
     * @return JSON string
     */
    public static String toJson(Object obj) {
        return gson.toJson(obj);
    }

    /**
     * Deserialize a JSON string to an object.
     *
     * @param json the JSON string
     * @param classOfT the target class
     * @return the deserialized object
     */
    public static <T> T fromJson(String json, Class<T> classOfT) {
        try {
            return gson.fromJson(json, classOfT);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Invalid JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Read the request body from an HttpExchange as a string.
     *
     * @param exchange the HttpExchange
     * @return request body as string
     */
    public static String readRequestBody(HttpExchange exchange) throws Exception {
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)
        );
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            body.append(line).append("\n");
        }
        reader.close();
        return body.toString().trim();
    }

    /**
     * Parse the request body as JSON into a target class.
     *
     * @param exchange the HttpExchange
     * @param classOfT the target class
     * @return parsed object
     */
    public static <T> T parseRequest(HttpExchange exchange, Class<T> classOfT) throws Exception {
        String body = readRequestBody(exchange);
        if (body.isEmpty()) {
            throw new IllegalArgumentException("Request body is empty");
        }
        return fromJson(body, classOfT);
    }

    /**
     * Write a JSON response to an HttpExchange.
     *
     * @param exchange the HttpExchange
     * @param statusCode the HTTP status code
     * @param response the response object
     */
    public static void sendResponse(HttpExchange exchange, int statusCode, Object response) throws Exception {
        String json = toJson(response);
        byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Content-Length", String.valueOf(responseBytes.length));
        
        // Add CORS headers for extension communication
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "chrome-extension://*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");

        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    /**
     * Write an error response to an HttpExchange.
     *
     * @param exchange the HttpExchange
     * @param statusCode the HTTP status code
     * @param error the error message
     */
    public static void sendError(HttpExchange exchange, int statusCode, String error) throws Exception {
        ErrorResponse errorResponse = new ErrorResponse(error, statusCode);
        sendResponse(exchange, statusCode, errorResponse);
    }

    /**
     * Send a 405 Method Not Allowed response.
     *
     * @param exchange the HttpExchange
     */
    public static void sendMethodNotAllowed(HttpExchange exchange) throws Exception {
        sendError(exchange, 405, "Method Not Allowed");
    }

    /**
     * Send a 401 Unauthorized response.
     *
     * @param exchange the HttpExchange
     */
    public static void sendUnauthorized(HttpExchange exchange) throws Exception {
        sendError(exchange, 401, "Unauthorized: Invalid or missing token");
    }

    /**
     * Send a 400 Bad Request response.
     *
     * @param exchange the HttpExchange
     * @param message the error message
     */
    public static void sendBadRequest(HttpExchange exchange, String message) throws Exception {
        sendError(exchange, 400, message);
    }

    /**
     * Send a 404 Not Found response.
     *
     * @param exchange the HttpExchange
     */
    public static void sendNotFound(HttpExchange exchange) throws Exception {
        sendError(exchange, 404, "Endpoint not found");
    }

    /**
     * Send a 500 Internal Server Error response.
     *
     * @param exchange the HttpExchange
     * @param message the error message
     */
    public static void sendInternalError(HttpExchange exchange, String message) throws Exception {
        sendError(exchange, 500, "Internal Server Error: " + message);
    }

    // ────────────────────────────────────────────────────────────────
    // ErrorResponse inner class
    // ────────────────────────────────────────────────────────────────

    private static class ErrorResponse {
        public boolean success = false;
        public String error;
        public int statusCode;

        ErrorResponse(String error, int statusCode) {
            this.error = error;
            this.statusCode = statusCode;
        }
    }
}
