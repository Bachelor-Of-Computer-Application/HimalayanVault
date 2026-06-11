package com.himalayanvault;
import com.himalayanvault.api.ApiServer;
import com.himalayanvault.db.DatabaseManager;
import com.himalayanvault.security.SessionManager;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Main — JavaFX Application entry point for Himalayan Vault.
 * 
 * Initializes the database and starts the lightweight API server
 * for Chrome extension communication on localhost:8443.
 *
 * Run:  javac + java  or  mvn javafx:run
 */
public class Main extends Application {

    private static final double AUTH_WIDTH = 1024;
    private static final double AUTH_HEIGHT = 640;
    private static final double AUTO_LOCK_MINUTES = 15;

    private ApiServer apiServer;
    private Stage primaryStage;
    private PauseTransition autoLockTimer;
    
    @Override
    
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;
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

        Scene scene = createAuthScene();

        primaryStage.setTitle("Himalayan Vault");
        primaryStage.setScene(scene);
        com.himalayanvault.ui.VaultController.configureAuthStage(primaryStage);

        // Centre on screen
        primaryStage.centerOnScreen();
        primaryStage.show();
        installAutoLock(scene);
        primaryStage.setOnCloseRequest(event -> {
            System.out.println("[Main] Application closing...");
            SessionManager.getInstance().lock();
            // Shutdown API server
            if (apiServer != null && apiServer.isRunning()) {
                apiServer.stop();
            }
        });
    }

    private Scene createAuthScene() throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/login.fxml"));
        Scene scene = new Scene(root, AUTH_WIDTH, AUTH_HEIGHT);
        scene.getStylesheets().add(getClass().getResource("/css/login.css").toExternalForm());
        return scene;
    }

    private void installAutoLock(Scene scene) {
        autoLockTimer = new PauseTransition(Duration.minutes(AUTO_LOCK_MINUTES));
        autoLockTimer.setOnFinished(event -> lockAndShowLogin());
        scene.addEventFilter(MouseEvent.ANY, event -> autoLockTimer.playFromStart());
        scene.addEventFilter(KeyEvent.ANY, event -> autoLockTimer.playFromStart());
        autoLockTimer.playFromStart();
    }

    private void lockAndShowLogin() {
        try {
            SessionManager.getInstance().lock();
            Scene scene = createAuthScene();
            primaryStage.setScene(scene);
            com.himalayanvault.ui.VaultController.configureAuthStage(primaryStage);
            installAutoLock(scene);
            primaryStage.centerOnScreen();
            System.out.println("[Main] Vault auto-locked after inactivity");
        } catch (Exception e) {
            System.err.println("[Main] Auto-lock failed: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
