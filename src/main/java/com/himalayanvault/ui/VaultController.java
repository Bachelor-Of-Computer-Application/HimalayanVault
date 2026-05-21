package com.himalayanvault.ui;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;

/**
 * VaultController — handles the main vault screen after unlock/signup.
 */
public class VaultController {

    @FXML private BorderPane root;
    @FXML private Label vaultStatus;

    private String currentUsername;

    /**
     * Set the current username for this vault session.
     * Called by LoginController when navigating to vault.
     *
     * @param username the logged-in username
     */
    public void setUsername(String username) {
        this.currentUsername = username;
        System.out.println("[VaultController] Vault opened for user: " + username);
        if (vaultStatus != null) {
            vaultStatus.setText("Vault: " + username);
        }
    }

    @FXML
    public void initialize(URL url, ResourceBundle rb) {
        System.out.println("[VaultController] Vault screen initialized");
    }

    @FXML
    private void handleLock() {
        System.out.println("[VaultController] Lock action - TODO: return to login");
    }

    @FXML
    private void handleMenu() {
        System.out.println("[VaultController] Menu action - TODO: show options");
    }
}
