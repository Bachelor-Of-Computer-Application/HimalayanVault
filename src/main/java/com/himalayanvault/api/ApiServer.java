package com.himalayanvault.api;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.himalayanvault.api.handlers.AuthHandler;
import com.himalayanvault.api.handlers.CredentialHandler;
import com.himalayanvault.api.handlers.HealthHandler;
import com.himalayanvault.api.handlers.PasswordHandler;
import com.himalayanvault.api.handlers.RecoveryHandler;
import com.himalayanvault.api.handlers.SignupHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * ApiServer — lightweight HTTP server for Himalayan Vault desktop application.
 * 
 * Listens on 127.0.0.1:8443 (localhost only for security).
 * Provides REST API endpoints for Chrome extension communication.
 * 
 * Endpoints:
 * - POST /signup     - Create a new vault account
 * - POST /login      - Authenticate and get session token
 * - POST /lock       - Logout/invalidate session
 * - GET /health      - Health check
 * - POST /generate-password - Generate secure password
 * - GET /credentials?site=   - Retrieve credentials for a site
 * - POST /save       - Save new credential
 * - POST /update     - Update existing credential
 * - POST /delete     - Delete credential
 * - GET /credential/{id} - Get credential by ID
 */
public class ApiServer {

    private static final String LOCALHOST = "127.0.0.1";
    private int port = 8443;
    private static final int THREAD_POOL_SIZE = 10;

    private HttpServer server;
    private ThreadPoolExecutor executor;
    private volatile boolean running = false;

    /**
     * Create and configure the API server.
     */
    public ApiServer() throws Exception {
        InetSocketAddress address = new InetSocketAddress(LOCALHOST, port);
        this.server = HttpServer.create(address, 50);  // Backlog of 50

        // Set up thread pool for handling requests
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.server.setExecutor(executor);

        // Register endpoints
        registerEndpoints();

        System.out.println("[ApiServer] Server configured on " + LOCALHOST + ":" + this.server.getAddress().getPort());
    }

    /**
     * Register all API endpoints with their handlers.
     */
    private void registerEndpoints() {
        // Health check endpoint
        server.createContext("/health", new HealthHandler());
        System.out.println("[ApiServer] Registered GET /health");

        // Authentication endpoints
        server.createContext("/signup", new SignupHandler());
        server.createContext("/login", new AuthHandler());
        server.createContext("/lock", new AuthHandler());
        System.out.println("[ApiServer] Registered POST /signup, POST /login, POST /lock");

        // Credential endpoints
        server.createContext("/credentials", new CredentialHandler());
        server.createContext("/save", new CredentialHandler());
        server.createContext("/update", new CredentialHandler());
        server.createContext("/delete", new CredentialHandler());
        server.createContext("/credential/", new CredentialHandler());
        System.out.println("[ApiServer] Registered credential endpoints");

        // Password generation endpoint
        server.createContext("/generate-password", new PasswordHandler());
        System.out.println("[ApiServer] Registered POST /generate-password");

        // Recovery endpoints
        server.createContext("/recovery/verify", new RecoveryHandler());
        server.createContext("/recovery/reset", new RecoveryHandler());
        System.out.println("[ApiServer] Registered POST /recovery/verify, POST /recovery/reset");
    }

    /**
     * Start the API server.
     */
    public void start() {
        if (running) {
            System.out.println("[ApiServer] Server already running");
            return;
        }

        try {
            server.start();
            running = true;
            System.out.println("[ApiServer] ✓ Server started on http://" + LOCALHOST + ":" + this.server.getAddress().getPort());
            System.out.println("[ApiServer] Listening for Chrome extension requests...");
        } catch (Exception e) {
            System.err.println("[ApiServer] Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Stop the API server gracefully.
     */
    public void stop() {
        if (!running) {
            System.out.println("[ApiServer] Server not running");
            return;
        }

        try {
            System.out.println("[ApiServer] Shutting down server...");
            
            // Stop accepting new connections
            server.stop(0);
            
            // Shutdown executor
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("[ApiServer] Executor did not terminate");
                }
            }

            running = false;
            System.out.println("[ApiServer] ✓ Server stopped");
        } catch (Exception e) {
            System.err.println("[ApiServer] Error stopping server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Check if server is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get server address.
     */
    public String getAddress() {
        return "http://" + LOCALHOST + ":" + this.server.getAddress().getPort();
    }

    /**
     * Main entry point — standalone server mode (for testing).
     */
    public static void main(String[] args) {
        try {
            ApiServer apiServer = new ApiServer();
            apiServer.start();

            // Add shutdown hook for graceful termination
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[ApiServer] Shutdown signal received");
                apiServer.stop();
            }));

            // Keep the server running
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("[ApiServer] Failed to start API server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
