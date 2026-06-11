package com.himalayanvault.ui;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

import com.himalayanvault.auth.AuthManager;
import com.himalayanvault.auth.RecoveryCodeManager;
import com.himalayanvault.export.VaultExporter;
import com.himalayanvault.security.ClipboardProtector;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * RecoveryController — handles password recovery using recovery codes (mnemonics).
 * Flow: 1) Enter recovery words, 2) Set new password, 3) Verify new password
 */
public class RecoveryController implements Initializable {

    private static final double AUTH_WIDTH = 1024;
    private static final double AUTH_HEIGHT = 640;

    /* ── Step panes ───────────────────────────────────────────────── */
    @FXML private VBox formWrapper;
    @FXML private VBox step1, step2, step2b, step3;  // step2b is export key mnemonic
    @FXML private Label stepLbl;

    /* ── Step 1: Enter Recovery Words ──────────────────────────────── */
    @FXML private TextField usernameField;
    @FXML private TextArea recoveryWordsArea;
    @FXML private Label err1;
    @FXML private Button nextBtn1, backBtn1;

    /* ── Step 2: Set New Password ──────────────────────────────────── */
    @FXML private PasswordField newPwdField, confirmPwdField;
    @FXML private TextField newPwdVis, confirmPwdVis;
    @FXML private Button eye1, eye2;
    @FXML private Rectangle seg1, seg2, seg3, seg4;
    @FXML private Label strengthLbl, matchLbl, err2;
    @FXML private Label r1, r2, r3, r4;
    @FXML private Button nextBtn2, backBtn2;

    /* ── Step 2b: Export Key Mnemonic ──────────────────────────── */
    @FXML private Label     exportKeyMnemonic;  // Display 12-word BIP39
    @FXML private Label     exportKeyWarning;
    @FXML private CheckBox  exportKeyWrittenCheck;  // Acknowledge written down
    @FXML private Label     err2b;
    @FXML private Button    nextBtn2b, backBtn2b;

    /* ── Step 3: Confirm Password Reset ──────────────────────────── */
    @FXML private Label confirmationMsg, err3;
    @FXML private Button completeBtn, backBtn3;

    /* ── State ───────────────────────────────────────────────────── */
    private boolean e1Open = false, e2Open = false;
    private String currentUsername; // Store username for use in step 3
    private String exportKeyMnemonicPhrase;  // Store for display
    private boolean onExportKeyStep = false;
    /* ── Colours ─────────────────────────────────────────────────── */
    private static final Color C_WEAK   = Color.web("#E24B4A");
    private static final Color C_FAIR   = Color.web("#EF9F27");
    private static final Color C_GOOD   = Color.web("#028090");
    private static final Color C_STRONG = Color.web("#2E6B0A");
    private static final Color C_EMPTY  = Color.web("#DDE3EE");

    /* ── Services ───────────────────────────────────────────────── */
    private final RecoveryCodeManager recovery = new RecoveryCodeManager();
    private final AuthManager auth = new AuthManager();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        Platform.runLater(this::playEntranceAnimation);
        // Password visibility toggle
        newPwdField.textProperty().addListener((o, old, v) -> {
            if (!e1Open) {
                newPwdVis.setText(v);
                updateBar(v);
                updateRules(v);
                updateMatch();
            }
        });
        newPwdVis.textProperty().addListener((o, old, v) -> {
            if (e1Open) {
                newPwdField.setText(v);
                updateBar(v);
                updateRules(v);
                updateMatch();
            }
        });
        confirmPwdField.textProperty().addListener((o, old, v) -> {
            if (!e2Open) {
                confirmPwdVis.setText(v);
                updateMatch();
            }
        });
        confirmPwdVis.textProperty().addListener((o, old, v) -> {
            if (e2Open) {
                confirmPwdField.setText(v);
                updateMatch();
            }
        });

        Platform.runLater(() -> recoveryWordsArea.requestFocus());
    }

    private void playEntranceAnimation() {
        animateNode(formWrapper, 0, 18);
        animateNode(step1, 60, 24);
    }

    /* ════════════════ STEP 1: Verify Recovery Words ════════════════ */
    @FXML
    private void step1Next() {
        String username = usernameField.getText().trim();
        String wordsInput = recoveryWordsArea.getText().trim();
        
        if (username.isBlank()) {
            err1.setText("Please enter your username.");
            return;
        }
        
        if (wordsInput.isBlank()) {
            err1.setText("Please enter your 16 recovery words separated by spaces.");
            return;
        }

        // Parse and verify recovery words
        String[] wordArray = wordsInput.toLowerCase().split("\\s+");
        if (wordArray.length != 16) {
            err1.setText("Please enter exactly 16 words. You provided: " + wordArray.length);
            return;
        }

        List<String> recoveryWords = Arrays.asList(wordArray);
        if (!recovery.verifyMnemonic(username, recoveryWords)) {
            err1.setText("Recovery words are incorrect for this username. Please check and try again.");
            return;
        }

        // Store username for use in later steps
        currentUsername = username;
        err1.setText("");
        System.out.println("[RecoveryController] Recovery words verified successfully for user: " + username);
        goStep(2);
    }

    @FXML
    private void backBtn1() {
        loadAuthScene("/fxml/login.fxml");
    }

    /* ════════════════ STEP 2: Set New Password ════════════════ */
    @FXML
    private void step2Next() {
        String pwd = newPwdField.getText();
        String con = confirmPwdField.getText();

        if (pwd.isBlank()) {
            err2.setText("Please enter a new master password.");
            return;
        }
        if (!meetsReqs(pwd)) {
            err2.setText("Password does not meet all requirements.");
            return;
        }
        if (!pwd.equals(con)) {
            err2.setText("Passwords do not match.");
            confirmPwdField.requestFocus();
            return;
        }

        err2.setText("");
        // Generate export key mnemonic
        exportKeyMnemonicPhrase = generateExportKeyMnemonic();
        exportKeyWrittenCheck.setSelected(false);
        err2b.setText("");
        if (exportKeyMnemonic != null) {
            exportKeyMnemonic.setText(exportKeyMnemonicPhrase);
        }
        onExportKeyStep = true;
        goStep(2);
    }

    @FXML
    private void step2bNext() {
        if (!exportKeyWrittenCheck.isSelected()) {
            err2b.setText("Please confirm you have written down the export key.");
            return;
        }
        err2b.setText("");
        onExportKeyStep = false;
        String pwd = newPwdField.getText();
        confirmationMsg.setText("New password: " + pwd.substring(0, Math.min(3, pwd.length())) + "***");
        goStep(3);
    }

    @FXML
    private void copyExportKey() {
        ClipboardProtector.copySensitiveText(exportKeyMnemonicPhrase);
    }

    private String generateExportKeyMnemonic() {
        try {
            VaultExporter exporter = new VaultExporter();
            // Generate a temporary empty credential list just to get the mnemonic
            com.himalayanvault.export.VaultExporter.ExportResult result = 
                exporter.exportWithBIP39Mnemonic(new java.util.ArrayList<>(), "recovery-export-key");
            return result.bip39Mnemonic;
        } catch (Exception e) {
            System.err.println("Failed to generate export key mnemonic: " + e.getMessage());
            return "error-generating-mnemonic";
        }
    }

    @FXML
    private void togglePwd1() {
        e1Open = swap(newPwdField, newPwdVis, e1Open, eye1);
    }

    @FXML
    private void togglePwd2() {
        e2Open = swap(confirmPwdField, confirmPwdVis, e2Open, eye2);
    }

    @FXML
    private void backBtn2() {
        goStep(1);
    }

    @FXML
    private void backBtn2b() {
        onExportKeyStep = false;
        exportKeyMnemonicPhrase = null;
        goStep(2);
    }

    /* ════════════════ STEP 3: Confirm Reset ════════════════ */
    @FXML
    private void completeRecovery() {
        String newPassword = newPwdField.getText();

        boolean success = auth.resetPasswordWithRecovery(currentUsername, newPassword);
        if (success) {
            err3.setText("");
            confirmationMsg.setText("Password reset successfully for user: " + currentUsername);
            completeBtn.setDisable(true);
            // Redirect to login after 2 seconds
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    Platform.runLater(() -> loadAuthScene("/fxml/login.fxml"));
                } catch (InterruptedException ignored) {}
            }, "recovery-complete").start();
        } else {
            err3.setText("Failed to reset password. Please try again.");
        }
    }

    @FXML
    private void backBtn3() {
        onExportKeyStep = true;
        goStep(2);
    }

    /* ──── Utility Methods ──────────────────────────────────────── */
    private void goStep(int s) {
        step1.setVisible(s == 1);
        step1.setManaged(s == 1);
        step2.setVisible(s == 2 && !onExportKeyStep);
        step2.setManaged(s == 2 && !onExportKeyStep);
        step2b.setVisible(s == 2 && onExportKeyStep);
        step2b.setManaged(s == 2 && onExportKeyStep);
        step3.setVisible(s == 3);
        step3.setManaged(s == 3);

        VBox activeStep = s == 1 ? step1 : s == 2 ? (onExportKeyStep ? step2b : step2) : step3;
        animateNode(activeStep, 0, 18);

        if (stepLbl != null) {
            String stepText = switch (s) {
                case 1 -> "Enter recovery code";
                case 2 -> onExportKeyStep ? "Save export key" : "Set new password";
                case 3 -> "Confirm password reset";
                default -> "";
            };
            stepLbl.setText("Step " + s + " of 3 — " + stepText);
        }
    }

    private void loadScene(String fxml, double w, double h) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxml));
            Stage stage = (Stage) step1.getScene().getWindow();
            Scene scene = new Scene(root, w, h);
            scene.getStylesheets().add(getClass().getResource("/css/login.css").toExternalForm());
            stage.setScene(scene);
            stage.setResizable(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            err1.setText("Navigation error: " + e.getMessage());
            System.err.println("[RecoveryController] Scene load error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadAuthScene(String fxml) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxml));
            Stage stage = (Stage) step1.getScene().getWindow();
            Scene scene = new Scene(root, AUTH_WIDTH, AUTH_HEIGHT);
            scene.getStylesheets().add(getClass().getResource("/css/login.css").toExternalForm());
            stage.setScene(scene);
            stage.setResizable(false);
            stage.setMinWidth(AUTH_WIDTH);
            stage.setMinHeight(AUTH_HEIGHT);
            stage.setMaxWidth(AUTH_WIDTH);
            stage.setMaxHeight(AUTH_HEIGHT);
            stage.centerOnScreen();
        } catch (IOException e) {
            err1.setText("Navigation error: " + e.getMessage());
            System.err.println("[RecoveryController] Scene load error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void animateNode(VBox node, long delayMs, double offsetY) {
        if (node == null) {
            return;
        }

        node.setOpacity(0);
        node.setTranslateY(offsetY);

        FadeTransition fade = new FadeTransition(Duration.millis(420), node);
        fade.setFromValue(0);
        fade.setToValue(1);

        TranslateTransition slide = new TranslateTransition(Duration.millis(420), node);
        slide.setFromY(offsetY);
        slide.setToY(0);

        ParallelTransition animation = new ParallelTransition(fade, slide);
        animation.setDelay(Duration.millis(delayMs));
        animation.play();
    }

    private boolean swap(PasswordField pwd, TextField vis, boolean isOpen, Button eye) {
        isOpen = !isOpen;
        if (isOpen) {
            vis.setText(pwd.getText());
            vis.setVisible(true);
            vis.setManaged(true);
            pwd.setVisible(false);
            pwd.setManaged(false);
            eye.setText("HIDE");
            Platform.runLater(() -> {
                vis.requestFocus();
                vis.positionCaret(vis.getText().length());
            });
        } else {
            pwd.setText(vis.getText());
            pwd.setVisible(true);
            pwd.setManaged(true);
            vis.setVisible(false);
            vis.setManaged(false);
            eye.setText("SHOW");
            Platform.runLater(() -> {
                pwd.requestFocus();
                pwd.positionCaret(pwd.getText().length());
            });
        }
        return isOpen;
    }

    private boolean meetsReqs(String p) {
        return p != null && p.length() >= 12
                && p.matches(".*[A-Z].*") && p.matches(".*[a-z].*")
                && p.matches(".*[0-9].*") && p.matches(".*[^A-Za-z0-9].*");
    }

    private void updateMatch() {
        String pwd = newPwdField.getText();
        String con = confirmPwdField.getText();
        if (pwd.equals(con) && !pwd.isBlank()) {
            matchLbl.setText("✓ Match");
            matchLbl.setStyle("-fx-text-fill:#2E6B0A;");
        } else {
            matchLbl.setText("");
        }
    }

    private void updateBar(String p) {
        int sc = 0;
        if (p != null && p.length() >= 8) sc++;
        if (p != null && p.length() >= 12) sc++;
        if (p != null && p.matches(".*[A-Z].*") && p.matches(".*[0-9].*")) sc++;
        if (p != null && p.matches(".*[^A-Za-z0-9].*")) sc++;

        Color[] cols = {C_WEAK, C_FAIR, C_GOOD, C_STRONG};
        Rectangle[] segs = {seg1, seg2, seg3, seg4};
        Color c = sc > 0 ? cols[sc - 1] : C_EMPTY;
        for (int i = 0; i < 4; i++) {
            segs[i].setFill(i < sc ? c : C_EMPTY);
        }

        if (sc > 0) {
            String[] lbl = {"Weak", "Fair", "Good", "Strong"};
            strengthLbl.setText("Strength: " + lbl[sc - 1]);
            strengthLbl.setStyle("-fx-text-fill:" + toHex(c) + ";");
        } else {
            strengthLbl.setText("");
        }
    }

    private void updateRules(String p) {
        if (p == null) p = "";
        if (r1 == null) return;
        applyRule(r1, p.length() >= 12);
        applyRule(r2, p.matches(".*[A-Z].*") && p.matches(".*[a-z].*"));
        applyRule(r3, p.matches(".*[0-9].*"));
        applyRule(r4, p.matches(".*[^A-Za-z0-9].*"));
    }

    private void applyRule(Label lbl, boolean met) {
        if (lbl == null) return;
        String rule = lbl.getText().replaceAll("^[✓○]\\s+", "");
        if (met) {
            lbl.setStyle("-fx-text-fill:#2E6B0A;");
            lbl.setText("✓ " + rule);
        } else {
            lbl.setStyle("-fx-text-fill:#7A8A9B;");
            lbl.setText("○ " + rule);
        }
    }

    private String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) (c.getRed() * 255), (int) (c.getGreen() * 255), (int) (c.getBlue() * 255));
    }
}
