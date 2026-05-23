package com.himalayanvault;
import com.himalayanvault.api.ApiServer;
import com.himalayanvault.db.DatabaseManager;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Main — JavaFX Application entry point for Himalayan Vault.
 * 
 * Initializes the database and starts the lightweight API server
 * for Chrome extension communication on localhost:8443.
 *
 * Run:  javac + java  or  mvn javafx:run
 */
public class Main extends Application {

    private ApiServer apiServer;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Initialize database on app startup
        DatabaseManager.getInstance();
        System.out.println("[Main] Database initialized");

        // Start the API server for Chrome extension communication
        try {
            apiServer = new ApiServer();
            apiServer.start();
        } catch (Exception e) {
            System.err.println("[Main] Failed to start API server: " + e.getMessage());
            e.printStackTrace();
        }

        Parent root = FXMLLoader.load(
            getClass().getResource("/fxml/login.fxml")
        );

        Scene scene = new Scene(root, 420, 560);

        primaryStage.setTitle("Himalayan Vault");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);

        // Centre on screen
        primaryStage.centerOnScreen();
        primaryStage.show();
        primaryStage.setOnCloseRequest(event -> {
            System.out.println("[Main] Application closing...");
            // Shutdown API server
            if (apiServer != null && apiServer.isRunning()) {
                apiServer.stop();
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
