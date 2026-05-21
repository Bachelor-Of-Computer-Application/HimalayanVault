package com.himalayanvault;
import com.himalayanvault.db.DatabaseManager;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Main — JavaFX Application entry point for Himalayan Vault.
 *
 * Run:  javac + java  or  mvn javafx:run
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Initialize database on app startup
        DatabaseManager.getInstance();
        System.out.println("[Main] Database initialized");

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
            // Perform any necessary cleanup here
        });
    }
    public static void main(String[] args) {
        launch(args);
    }
}
