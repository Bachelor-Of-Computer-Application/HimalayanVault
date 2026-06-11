package com.himalayanvault;

import com.himalayanvault.api.ApiServer;
import com.himalayanvault.db.DatabaseManager;

/**
 * ApiOnlyMain — starts the Database and ApiServer without JavaFX UI.
 * Useful for running the backend API for the browser extension in headless mode.
 */
public class ApiOnlyMain {

    public static void main(String[] args) {
        try {
            DatabaseManager.getInstance();
            System.out.println("[ApiOnlyMain] Database initialized");

            ApiServer apiServer = new ApiServer();
            apiServer.start();
            System.out.println("[ApiOnlyMain] API server started on http://127.0.0.1:8443");

            // Keep the main thread alive while the server runs
            synchronized (ApiOnlyMain.class) {
                ApiOnlyMain.class.wait();
            }
        } catch (InterruptedException ie) {
            System.out.println("[ApiOnlyMain] Interrupted, shutting down");
        } catch (Exception e) {
            System.err.println("[ApiOnlyMain] Failed to start API server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
