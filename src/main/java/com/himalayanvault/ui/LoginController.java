package com.himalayanvault.ui;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;

import com.himalayanvault.auth.AuthManager;
import com.himalayanvault.auth.BiometricHandler;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * LoginController — handles master password entry, biometric unlock,
 * password-strength display, attempt lockout, and navigation to vault.
 */
public class LoginController implements Initializable {

    // ── FXML injected fields ──────────────────────────────────────────
    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField     visibleField;
    @FXML private Button        eyeButton;
    @FXML private Button        unlockButton;

    @FXML private Rectangle seg1, seg2, seg3, seg4;
    @FXML private Label     strengthLabel;

    @FXML private HBox  warnBox;
    @FXML private Label warnLabel;

    // ── Constants ─────────────────────────────────────────────────────
    private static final int    MAX_ATTEMPTS      = 5;
    private static final int    LOCKOUT_SECONDS   = 30;
    private static final String WEAK_COLOR        = "#E24B4A";
    private static final String FAIR_COLOR        = "#EF9F27";
    private static final String GOOD_COLOR        = "#028090";
    private static final String STRONG_COLOR      = "#2E6B0A";
    private static final String EMPTY_COLOR       = "#DDE3EE";

    // ── State ─────────────────────────────────────────────────────────
    private final AtomicInteger failedAttempts = new AtomicInteger(0);
    private boolean             passwordVisible = false;
    private Timeline            lockoutTimer;

    // ── Services ──────────────────────────────────────────────────────
    private final AuthManager     authManager     = new AuthManager();
    private final BiometricHandler biometricHandler = new BiometricHandler();

    // ─────────────────────────────────────────────────────────────────
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Sync PasswordField ↔ TextField for eye toggle
        passwordField.textProperty().addListener((obs, old, val) -> {
            if (!passwordVisible) {
                visibleField.setText(val);
                updateStrengthBar(val);
            }
        });
        visibleField.textProperty().addListener((obs, old, val) -> {
            if (passwordVisible) {
                passwordField.setText(val);
                updateStrengthBar(val);
            }
        });

        // Auto-focus password field
        Platform.runLater(() -> passwordField.requestFocus());
    }

    // ── Unlock ────────────────────────────────────────────────────────
    @FXML
    private void handleUnlock() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username == null || username.isBlank()) {
            shakeField();
            showWarning("Please enter your username.", false);
            return;
        }

        if (password == null || password.isBlank()) {
            shakeField();
            showWarning("Please enter your master password.", false);
            return;
        }

        boolean valid = authManager.verifyMasterPassword(username, password);

        if (valid) {
            failedAttempts.set(0);
            navigateToVault(username);
        } else {
            int attempts = failedAttempts.incrementAndGet();
            int remaining = MAX_ATTEMPTS - attempts;
            clearPassword();

            if (remaining > 0) {
                showWarning(
                    "Incorrect password. " + remaining +
                    " attempt" + (remaining == 1 ? "" : "s") + " remaining.",
                    false
                );
            } else {
                startLockout();
            }
        }
    }

    // ── Biometric ─────────────────────────────────────────────────────
    @FXML
    private void handleBiometric() {
        String username = usernameField.getText();
        if (username == null || username.isBlank()) {
            showWarning("Please enter your username first.", false);
            return;
        }

        showInfo("Biometric prompt opened — waiting for Windows Hello / Touch ID…");
        unlockButton.setDisable(true);

        // Run biometric check off the JavaFX thread
        new Thread(() -> {
            boolean success = biometricHandler.authenticate();
            Platform.runLater(() -> {
                unlockButton.setDisable(false);
                if (success) {
                    navigateToVault(username);
                } else {
                    showWarning("Biometric authentication failed. Try master password.", false);
                }
            });
        }, "biometric-thread").start();
    }

    // ── Recovery ──────────────────────────────────────────────────────
    @FXML
    private void handleRecovery(MouseEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/recovery.fxml")
            );
            loader.setClassLoader(getClass().getClassLoader());
            Parent root = loader.load();
            Stage stage = (Stage) unlockButton.getScene().getWindow();
            stage.setScene(new Scene(root, 480, 680));
            stage.setTitle("Himalayan Vault - Password Recovery");
            stage.setResizable(true);
            stage.centerOnScreen();
            System.out.println("[LoginController] Recovery screen opened successfully");
        } catch (IOException e) {
            System.err.println("[LoginController] Failed to open recovery screen");
            e.printStackTrace();
            showWarning("Recovery Error: " + e.getMessage(), false);
        }
    }

    // ── Eye Toggle ────────────────────────────────────────────────────
    @FXML
    private void togglePasswordVisibility() {
        passwordVisible = !passwordVisible;

        if (passwordVisible) {
            visibleField.setText(passwordField.getText());
            visibleField.setVisible(true);
            visibleField.setManaged(true);
            passwordField.setVisible(false);
            passwordField.setManaged(false);
            eyeButton.setText("🙈");
            Platform.runLater(() -> {
                visibleField.requestFocus();
                visibleField.positionCaret(visibleField.getText().length());
            });
        } else {
            passwordField.setText(visibleField.getText());
            passwordField.setVisible(true);
            passwordField.setManaged(true);
            visibleField.setVisible(false);
            visibleField.setManaged(false);
            eyeButton.setText("👁");
            Platform.runLater(() -> {
                passwordField.requestFocus();
                passwordField.positionCaret(passwordField.getText().length());
            });
        }
    }

    // ── Strength Bar ──────────────────────────────────────────────────
    private void updateStrengthBar(String password) {
        if (password == null || password.isEmpty()) {
            setAllSegments(EMPTY_COLOR);
            strengthLabel.setText("");
            return;
        }

        int score = 0;
        if (password.length() >= 8)                                    score++;
        if (password.length() >= 12)                                   score++;
        if (password.matches(".*[A-Z].*") && password.matches(".*[0-9].*")) score++;
        if (password.matches(".*[^A-Za-z0-9].*"))                      score++;

        String color;
        String label;
        switch (score) {
            case 1 -> { color = WEAK_COLOR;   label = "Weak"; }
            case 2 -> { color = FAIR_COLOR;   label = "Fair"; }
            case 3 -> { color = GOOD_COLOR;   label = "Good"; }
            case 4 -> { color = STRONG_COLOR; label = "Strong"; }
            default -> { color = EMPTY_COLOR; label = ""; }
        }

        Rectangle[] segs = { seg1, seg2, seg3, seg4 };
        for (int i = 0; i < 4; i++) {
            segs[i].setFill(Color.web(i < score ? color : EMPTY_COLOR));
        }
        strengthLabel.setText("Strength: " + label);
        strengthLabel.setStyle("-fx-text-fill: " + color + ";");
    }

    private void setAllSegments(String hex) {
        Color c = Color.web(hex);
        seg1.setFill(c); seg2.setFill(c); seg3.setFill(c); seg4.setFill(c);
    }

    // ── Lockout ───────────────────────────────────────────────────────
    private void startLockout() {
        unlockButton.setDisable(true);
        AtomicInteger countdown = new AtomicInteger(LOCKOUT_SECONDS);

        lockoutTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            int sec = countdown.decrementAndGet();
            if (sec <= 0) {
                lockoutTimer.stop();
                failedAttempts.set(0);
                unlockButton.setDisable(false);
                hideWarning();
            } else {
                showWarning(
                    "Too many failed attempts. Vault locked for " + sec + " seconds.",
                    false
                );
            }
        }));
        lockoutTimer.setCycleCount(LOCKOUT_SECONDS);
        lockoutTimer.play();
        showWarning(
            "Too many failed attempts. Vault locked for " + LOCKOUT_SECONDS + " seconds.",
            false
        );
    }

    // ── Navigation ────────────────────────────────────────────────────
    private void navigateToVault(String username) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/vault.fxml")
            );
            Parent root = loader.load();
            VaultController controller = loader.getController();
            controller.setUsername(username);
            Stage stage = (Stage) unlockButton.getScene().getWindow();
            stage.setScene(new Scene(root, 900, 620));
            stage.setTitle("Himalayan Vault");
        } catch (IOException e) {
            showWarning("Could not load vault screen: " + e.getMessage(), false);
        }
    }

    @FXML
    private void navigateToSignup(MouseEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/signup.fxml")
            );
            Parent root = loader.load();
            Stage stage = (Stage) unlockButton.getScene().getWindow();
            stage.setScene(new Scene(root, 420, 540));
            stage.setTitle("Himalayan Vault - Create New Vault");
        } catch (IOException e) {
            showWarning("Could not load signup screen: " + e.getMessage(), false);
        }
    }

    // ── Warning helpers ───────────────────────────────────────────────
    private void showWarning(String message, boolean isInfo) {
        warnLabel.setText(message);
        warnBox.setVisible(true);
        warnBox.setManaged(true);
        if (isInfo) {
            warnBox.getStyleClass().add("info");
        } else {
            warnBox.getStyleClass().remove("info");
        }
    }

    private void showInfo(String message) {
        showWarning(message, true);
    }

    private void hideWarning() {
        warnBox.setVisible(false);
        warnBox.setManaged(false);
    }

    private void clearPassword() {
        passwordField.clear();
        visibleField.clear();
        setAllSegments(EMPTY_COLOR);
        strengthLabel.setText("");
        Platform.runLater(() -> passwordField.requestFocus());
    }

    // ── Shake animation ───────────────────────────────────────────────
    private void shakeField() {
        HBox wrap = (HBox) passwordField.getParent();
        Timeline shake = new Timeline(
            new KeyFrame(Duration.millis(0),   e -> wrap.setTranslateX(0)),
            new KeyFrame(Duration.millis(50),  e -> wrap.setTranslateX(-6)),
            new KeyFrame(Duration.millis(100), e -> wrap.setTranslateX(6)),
            new KeyFrame(Duration.millis(150), e -> wrap.setTranslateX(-4)),
            new KeyFrame(Duration.millis(200), e -> wrap.setTranslateX(4)),
            new KeyFrame(Duration.millis(250), e -> wrap.setTranslateX(0))
        );
        shake.play();
    }
}
