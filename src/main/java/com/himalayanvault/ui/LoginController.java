package com.himalayanvault.ui;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;

import com.himalayanvault.auth.AuthManager;
import com.himalayanvault.auth.BiometricHandler;
import com.himalayanvault.security.CredentialKeyDerivation;
import com.himalayanvault.security.SessionManager;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * password-strength display, attempt lockout, and navigation to vault.
 */
public class LoginController implements Initializable {

    private static final double AUTH_WIDTH = 1024;
    private static final double AUTH_HEIGHT = 640;

    // ── FXML injected fields ──────────────────────────────────────────
    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button        eyeButton;
    @FXML private Button        unlockButton;
    @FXML private StackPane     rootPane;
    @FXML private HBox          contentShell;
    @FXML private VBox          heroPanel;
    @FXML private VBox          formWrapper;
    @FXML private HBox          headerSection;
    @FXML private Label         formTitle;
    @FXML private HBox          buttonContainer;
    @FXML private HBox          linksContainer;

    @FXML private Rectangle seg1, seg2, seg3, seg4;
    @FXML private Label     strengthLabel;

    @FXML private HBox warnBox;
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
    private Timeline            lockoutTimer;

    // ── Services ──────────────────────────────────────────────────────
    private final AuthManager     authManager     = new AuthManager();
    private final BiometricHandler biometricHandler = new BiometricHandler();

    // ─────────────────────────────────────────────────────────────────
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        makeLayoutResponsive();
        Platform.runLater(this::playEntranceAnimation);

        // Sync PasswordField ↔ TextField for eye toggle
        passwordField.textProperty().addListener((obs, old, val) -> {
            updateStrengthBar(val);
        });

        // Auto-focus password field
        Platform.runLater(() -> passwordField.requestFocus());
    }

    private void makeLayoutResponsive() {
        usernameField.setMaxWidth(Double.MAX_VALUE);
        passwordField.setMaxWidth(Double.MAX_VALUE);
        warnBox.setMaxWidth(Double.MAX_VALUE);

        rootPane.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene == null) {
                return;
            }

            ChangeListener<Number> resizeListener = (change, oldValue, newValue) ->
                applyResponsiveLayout(scene.getWidth());

            scene.widthProperty().addListener(resizeListener);
            scene.heightProperty().addListener(resizeListener);
            Platform.runLater(() -> applyResponsiveLayout(scene.getWidth()));
        });
    }

    private void applyResponsiveLayout(double sceneWidth) {
        double width = Math.max(sceneWidth, 320);
        boolean compact = width < 560;

        double cardWidth = compact ? Math.min(width - 24, 360) : Math.min(width * 0.42, 380);
        double contentWidth = Math.max(260, cardWidth - 24);
        double edgePadding = compact ? 14 : 40;

        headerSection.setPadding(new Insets(compact ? 16 : 20, edgePadding, compact ? 16 : 20, edgePadding));
        headerSection.setSpacing(compact ? 10 : 12);
        if (contentShell != null) {
            contentShell.setSpacing(compact ? 16 : 28);
            contentShell.setPadding(new Insets(compact ? 16 : 24, compact ? 20 : 40, compact ? 24 : 34, compact ? 20 : 40));
        }
        if (heroPanel != null) {
            heroPanel.setVisible(!compact);
            heroPanel.setManaged(!compact);
        }
        formWrapper.setMaxWidth(cardWidth);
        formWrapper.setPrefWidth(cardWidth);
        formWrapper.setSpacing(compact ? 10 : 12);
        formWrapper.setPadding(new Insets(compact ? 20 : 22, compact ? 20 : 24, compact ? 20 : 22, compact ? 20 : 24));

        usernameField.setPrefWidth(contentWidth);
        passwordField.setPrefWidth(contentWidth);
        warnBox.setMaxWidth(contentWidth);

        buttonContainer.setSpacing(compact ? 8 : 12);
        buttonContainer.setMaxWidth(contentWidth);
        linksContainer.setSpacing(compact ? 10 : 16);
        linksContainer.setMaxWidth(contentWidth);

        if (formTitle != null) {
            formTitle.setStyle(compact
                ? "-fx-font-size: 20px; -fx-font-weight: 800; -fx-letter-spacing: 1px;"
                : "-fx-font-size: 24px; -fx-font-weight: 800; -fx-letter-spacing: 1px;");
        }

        if (compact) {
            VBox.setMargin(formWrapper, new Insets(0, 12, 12, 12));
        } else {
            VBox.setMargin(formWrapper, new Insets(0, 20, 18, 20));
        }
    }

    private void playEntranceAnimation() {
        if (heroPanel != null) {
            heroPanel.setOpacity(0);
            heroPanel.setTranslateX(-26);
        }
        if (formWrapper != null) {
            formWrapper.setOpacity(0);
            formWrapper.setTranslateX(28);
        }

        FadeTransition heroFade = new FadeTransition(Duration.millis(520), heroPanel);
        heroFade.setFromValue(0);
        heroFade.setToValue(1);
        TranslateTransition heroSlide = new TranslateTransition(Duration.millis(520), heroPanel);
        heroSlide.setFromX(-26);
        heroSlide.setToX(0);

        FadeTransition formFade = new FadeTransition(Duration.millis(620), formWrapper);
        formFade.setFromValue(0);
        formFade.setToValue(1);
        TranslateTransition formSlide = new TranslateTransition(Duration.millis(620), formWrapper);
        formSlide.setFromX(28);
        formSlide.setToX(0);

        ParallelTransition heroAnim = new ParallelTransition(heroFade, heroSlide);
        ParallelTransition formAnim = new ParallelTransition(formFade, formSlide);
        heroAnim.play();
        formAnim.setDelay(Duration.millis(80));
        formAnim.play();
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
            String token = SessionManager.getInstance().createSession(
                    username,
                    password,
                    CredentialKeyDerivation.saltForUser(username));
            navigateToVault(username, token);
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
    private void handleRecovery() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/recovery.fxml")
            );
            loader.setClassLoader(getClass().getClassLoader());
            Parent root = loader.load();
            Stage stage = (Stage) unlockButton.getScene().getWindow();
            Scene scene = new Scene(root, AUTH_WIDTH, AUTH_HEIGHT);
            scene.getStylesheets().add(getClass().getResource("/css/recovery.css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle("Himalayan Vault - Password Recovery");
            VaultController.configureAuthStage(stage);
            stage.centerOnScreen();
            System.out.println("[LoginController] Recovery screen opened successfully");
        } catch (IOException e) {
            System.err.println("[LoginController] Failed to open recovery screen");
            e.printStackTrace();
            showWarning("Recovery Error: " + e.getMessage(), false);
        }
    }

    // ── Eye Toggle ────────────────────────────────────────────────────
    // ── Strength Bar ──────────────────────────────────────────────────
    private void updateStrengthBar(String password) {
        if (password == null || password.isEmpty()) {
            if (seg1 != null) setAllSegments(EMPTY_COLOR);
            if (strengthLabel != null) strengthLabel.setText("");
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

        // If any strength rectangle is missing, avoid touching them to prevent NPEs
        if (seg1 == null || seg2 == null || seg3 == null || seg4 == null) {
            if (strengthLabel != null) {
                strengthLabel.setText("Strength: " + label);
                strengthLabel.setStyle("-fx-text-fill: " + color + ";");
            }
            return;
        }

        Rectangle[] segs = { seg1, seg2, seg3, seg4 };
        for (int i = 0; i < 4; i++) {
            segs[i].setFill(Color.web(i < score ? color : EMPTY_COLOR));
        }
        if (strengthLabel != null) {
            strengthLabel.setText("Strength: " + label);
            strengthLabel.setStyle("-fx-text-fill: " + color + ";");
        }
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
        navigateToVault(username, null);
    }

    private void navigateToVault(String username, String token) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/vault.fxml")
            );
            Parent root = loader.load();
            VaultController controller = loader.getController();
            if (token != null) {
                controller.setSession(token);
            }
            controller.setUsername(username);
            Stage stage = (Stage) unlockButton.getScene().getWindow();
            Scene scene = new Scene(root, VaultController.VAULT_WIDTH, VaultController.VAULT_HEIGHT);
            scene.getStylesheets().add(getClass().getResource("/css/vault.css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle("Himalayan Vault");
            VaultController.configureVaultStage(stage);
            stage.centerOnScreen();
        } catch (Exception e) {
            e.printStackTrace();
            showWarning("Could not load vault screen: " + e.getMessage(), false);
        }
    }

    @FXML
    private void navigateToSignup() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/signup.fxml")
            );
            Parent root = loader.load();
            Stage stage = (Stage) unlockButton.getScene().getWindow();
            Scene scene = new Scene(root, SignupController.SIGNUP_WIDTH, SignupController.SIGNUP_HEIGHT);
            scene.getStylesheets().add(getClass().getResource("/css/signup.css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle("Himalayan Vault - Create New Vault");
            SignupController.configureSignupStage(stage);
            stage.centerOnScreen();
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
        setAllSegments(EMPTY_COLOR);
        strengthLabel.setText("");
        Platform.runLater(() -> passwordField.requestFocus());
    }

    // ── Shake animation ───────────────────────────────────────────────
    private void shakeField() {
        // Shake the password field directly
        Timeline shake = new Timeline(
            new KeyFrame(Duration.millis(0),   e -> passwordField.setTranslateX(0)),
            new KeyFrame(Duration.millis(50),  e -> passwordField.setTranslateX(-6)),
            new KeyFrame(Duration.millis(100), e -> passwordField.setTranslateX(6)),
            new KeyFrame(Duration.millis(150), e -> passwordField.setTranslateX(-4)),
            new KeyFrame(Duration.millis(200), e -> passwordField.setTranslateX(4)),
            new KeyFrame(Duration.millis(250), e -> passwordField.setTranslateX(0))
        );
        shake.play();
    }
}
