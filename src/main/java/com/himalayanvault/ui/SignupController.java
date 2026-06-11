package com.himalayanvault.ui;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import com.himalayanvault.auth.AuthManager;
import com.himalayanvault.auth.RecoveryCodeManager;
import com.himalayanvault.export.VaultExporter;
import com.himalayanvault.security.ClipboardProtector;
import com.himalayanvault.security.CredentialKeyDerivation;
import com.himalayanvault.security.SessionManager;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

public class SignupController implements Initializable {

    public static final double SIGNUP_WIDTH = 1024;
    public static final double SIGNUP_HEIGHT = 720;

    /* ── Step panes ───────────────────────────────────────────────── */
    @FXML private VBox formWrapper;
    @FXML private VBox step1, step2, step2b, step3;

    /* ── Step dots / lines ───────────────────────────────────────── */
    @FXML private StackPane dot1, dot2, dot3;
    @FXML private Rectangle line1, line2;
    @FXML private Label     stepLbl;

    /* ── Step 1 ──────────────────────────────────────────────────── */
    @FXML private TextField     usernameField;
    @FXML private PasswordField pwdField, confirmField;
    @FXML private TextField     pwdVis,  confirmVis;
    @FXML private Button        eye1,    eye2;
    @FXML private Rectangle     seg1, seg2, seg3, seg4;
    @FXML private Label         strengthLbl, matchLbl;
    @FXML private Label         r1, r2, r3, r4, err1;

    /* ── Step 2: Recovery Code ──────────────────────────────────── */
    @FXML private GridPane  wordGrid;
    @FXML private CheckBox  savedCheck;
    @FXML private Label     err2;

    /* ── Step 2b: Export Key Mnemonic ──────────────────────────── */
    @FXML private GridPane  exportKeyGrid;
    @FXML private Label     exportKeyWarning;
    @FXML private CheckBox  exportKeyWrittenCheck;
    @FXML private Label     err2b;

    /* ── Step 3 ──────────────────────────────────────────────────── */
    @FXML private TextField v1, v2, v3;
    @FXML private Label     vlbl1, vlbl2, vlbl3, err3;

    /* ── State ───────────────────────────────────────────────────── */
    private boolean         e1Open = false, e2Open = false;
    private List<String>    words;
    private String          exportKeyMnemonicPhrase;
    private boolean         onExportKeyStep = false;
    private static final int[] CHK = { 2, 6, 11 };

    /* ── Colours ─────────────────────────────────────────────────── */
    private static final Color C_WEAK   = Color.web("#E24B4A");
    private static final Color C_FAIR   = Color.web("#EF9F27");
    private static final Color C_GOOD   = Color.web("#028090");
    private static final Color C_STRONG = Color.web("#2E6B0A");
    private static final Color C_EMPTY  = Color.web("#DDE3EE");

    private final AuthManager         auth     = new AuthManager();
    private final RecoveryCodeManager recovery = new RecoveryCodeManager();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        Platform.runLater(this::playEntranceAnimation);
        pwdField.textProperty().addListener((o, old, v) -> { if (!e1Open) { pwdVis.setText(v); updateBar(v); updateRules(v); updateMatch(); } });
        pwdVis.textProperty().addListener((o, old, v) ->   { if (e1Open)  { pwdField.setText(v); updateBar(v); updateRules(v); updateMatch(); } });
        confirmField.textProperty().addListener((o, old, v) -> { if (!e2Open) { confirmVis.setText(v); updateMatch(); } });
        confirmVis.textProperty().addListener((o, old, v) ->   { if (e2Open)  { confirmField.setText(v); updateMatch(); } });
        Platform.runLater(() -> pwdField.requestFocus());
    }

    public static void configureSignupStage(Stage stage) {
        stage.setResizable(false);
        stage.setMinWidth(SIGNUP_WIDTH);
        stage.setMinHeight(SIGNUP_HEIGHT);
        stage.setMaxWidth(SIGNUP_WIDTH);
        stage.setMaxHeight(SIGNUP_HEIGHT);
    }

    private void playEntranceAnimation() {
        animateNode(formWrapper, 0, 18);
        animateNode(step1, 60, 24);
    }

    @FXML private void step1Next() {
        String username = usernameField.getText();
        String pwd = pwdField.getText();
        String con = confirmField.getText();

        if (username.isBlank()) { err1.setText("Please enter a username."); return; }
        if (pwd.isBlank()) { err1.setText("Please enter a master password."); return; }
        if (!meetsReqs(pwd)) { err1.setText("Password does not meet all requirements."); return; }
        if (!pwd.equals(con)) { err1.setText("Passwords do not match."); confirmField.requestFocus(); return; }

        err1.setText("");
        words = recovery.generateMnemonic();
        exportKeyMnemonicPhrase = generateExportKeyMnemonic();
        buildGrid();
        goStep(2);
    }

    @FXML private void togglePwd1() { e1Open = swap(pwdField, pwdVis, e1Open, eye1); }
    @FXML private void togglePwd2() { e2Open = swap(confirmField, confirmVis, e2Open, eye2); }

    @FXML private void step2Next() {
        if (!savedCheck.isSelected()) {
            err2.setText("Please confirm you have saved all 16 words.");
            return;
        }
        err2.setText("");
        exportKeyWrittenCheck.setSelected(false);
        err2b.setText("");
        buildExportKeyGrid();
        onExportKeyStep = true;
        goStep(2);
    }

    @FXML private void step2bNext() {
        if (!exportKeyWrittenCheck.isSelected()) {
            err2b.setText("Please confirm you have written down the export key.");
            return;
        }
        err2b.setText("");
        onExportKeyStep = false;
        vlbl1.setText("Word #" + (CHK[0] + 1));
        vlbl2.setText("Word #" + (CHK[1] + 1));
        vlbl3.setText("Word #" + (CHK[2] + 1));
        v1.clear();
        v2.clear();
        v3.clear();
        goStep(3);
    }

    @FXML private void back2b() {
        onExportKeyStep = false;
        goStep(2);
    }

    @FXML private void copyExportKey() {
        ClipboardProtector.copySensitiveText(exportKeyMnemonicPhrase);
    }

    private String generateExportKeyMnemonic() {
        try {
            VaultExporter exporter = new VaultExporter();
            com.himalayanvault.export.VaultExporter.ExportResult result =
                exporter.exportWithBIP39Mnemonic(new java.util.ArrayList<>(), "signup-export-key");
            return result.bip39Mnemonic;
        } catch (Exception e) {
            System.err.println("Failed to generate export key mnemonic: " + e.getMessage());
            return "error-generating-mnemonic";
        }
    }

    @FXML private void copyWords() {
        if (words == null) return;
        ClipboardProtector.copySensitiveText(String.join(" ", words));
    }

    @FXML private void createVault() {
        String w1 = v1.getText().trim().toLowerCase();
        String w2 = v2.getText().trim().toLowerCase();
        String w3 = v3.getText().trim().toLowerCase();
        boolean ok = words.get(CHK[0]).equals(w1)
                  && words.get(CHK[1]).equals(w2)
                  && words.get(CHK[2]).equals(w3);
        if (!ok) {
            err3.setText("One or more words are incorrect. Check your written copy.");
            if (!words.get(CHK[0]).equals(w1))       v1.requestFocus();
            else if (!words.get(CHK[1]).equals(w2))  v2.requestFocus();
            else                                      v3.requestFocus();
            return;
        }
        err3.setText("");
        try {
            String username = usernameField.getText();
            String password = pwdField.getText();
            auth.setMasterPassword(username, password);
            recovery.storeHashedMnemonic(username, words);

            String token = SessionManager.getInstance().createSession(
                    username,
                    password,
                    CredentialKeyDerivation.saltForUser(username));

            openVault(token, username);
        } catch (Exception e) {
            err3.setText("Vault creation error: " + e.getMessage());
        }
    }

    @FXML private void back1() { goStep(1); }
    @FXML private void back2() {
        onExportKeyStep = true;
        goStep(2);
    }
    @FXML private void switchToLogin() { loadAuthScene("/fxml/login.fxml"); }

    private void loadAuthScene(String fxml) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxml));
            Stage stage = (Stage) step1.getScene().getWindow();
            Scene scene = new Scene(root, 1024, 640);
            scene.getStylesheets().add(getClass().getResource("/css/login.css").toExternalForm());
            stage.setScene(scene);
            VaultController.configureAuthStage(stage);
            stage.centerOnScreen();
        } catch (IOException e) {
            err1.setText("Navigation error: " + e.getMessage());
        }
    }

    private void openVault(String token, String username) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/vault.fxml"));
        Parent root = loader.load();
        VaultController controller = loader.getController();
        controller.setSession(token);
        controller.setUsername(username);

        Stage stage = (Stage) step1.getScene().getWindow();
        Scene scene = new Scene(root, VaultController.VAULT_WIDTH, VaultController.VAULT_HEIGHT);
        scene.getStylesheets().add(getClass().getResource("/css/vault.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle("Himalayan Vault");
        VaultController.configureVaultStage(stage);
        stage.centerOnScreen();
    }

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

        if (dot1 != null) {
            setDot(dot1, s == 1 ? "active" : s > 1 ? "complete" : "inactive");
            setDot(dot2, s == 2 ? "active" : s > 2 ? "complete" : "inactive");
            setDot(dot3, s == 3 ? "active" : "inactive");
            line1.setFill(Color.web(s > 1 ? "#2E6B0A" : "#3C5A8A"));
            line2.setFill(Color.web(s > 2 ? "#2E6B0A" : "#3C5A8A"));
        }

        if (stepLbl != null) {
            String stepText = switch (s) {
                case 1 -> "Set master password";
                case 2 -> onExportKeyStep ? "Save export key" : "Save recovery code";
                case 3 -> "Verify recovery code";
                default -> "";
            };
            stepLbl.setText("Step " + s + " of 3 — " + stepText);
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

    private void setDot(StackPane dot, String state) {
        dot.getStyleClass().removeAll("step-active", "step-inactive", "step-complete");
        dot.getStyleClass().add("step-" + state);
    }

    private void buildGrid() {
        if (words == null || wordGrid == null) {
            return;
        }

        wordGrid.getChildren().clear();
        int columns = 4;
        double cellWidth = 98;

        for (int i = 0; i < words.size(); i++) {
            VBox cell = new VBox(2);
            cell.getStyleClass().add("word-cell");
            cell.setPadding(new Insets(4, 6, 4, 6));
            cell.setMinWidth(cellWidth);
            cell.setPrefWidth(cellWidth);
            cell.setMaxWidth(cellWidth);

            Label num = new Label((i + 1) + ".");
            num.getStyleClass().add("word-num");
            Label word = new Label(words.get(i));
            word.getStyleClass().add("word-val");
            word.setWrapText(true);
            word.setMaxWidth(cellWidth - 10);

            cell.getChildren().addAll(num, word);
            wordGrid.add(cell, i % columns, i / columns);
        }
    }

    private void buildExportKeyGrid() {
        if (exportKeyGrid == null || exportKeyMnemonicPhrase == null || exportKeyMnemonicPhrase.isBlank()) {
            return;
        }

        String[] exportWords = exportKeyMnemonicPhrase.trim().split("\\s+");
        exportKeyGrid.getChildren().clear();

        int columns = 3;
        double cellWidth = 130;

        for (int i = 0; i < exportWords.length; i++) {
            VBox cell = new VBox(2);
            cell.getStyleClass().add("word-cell");
            cell.setPadding(new Insets(4, 6, 4, 6));
            cell.setMinWidth(cellWidth);
            cell.setPrefWidth(cellWidth);
            cell.setMaxWidth(cellWidth);

            Label num = new Label((i + 1) + ".");
            num.getStyleClass().add("word-num");
            Label word = new Label(exportWords[i]);
            word.getStyleClass().add("word-val");
            word.setWrapText(true);
            word.setMaxWidth(cellWidth - 10);

            cell.getChildren().addAll(num, word);
            exportKeyGrid.add(cell, i % columns, i / columns);
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
        for (int i = 0; i < 4; i++) segs[i].setFill(i < sc ? c : C_EMPTY);
        if (sc > 0) {
            String[] lbl = {"Weak", "Fair", "Good", "Strong"};
            strengthLbl.setText("Strength: " + lbl[sc - 1]);
            strengthLbl.setStyle("-fx-text-fill:" + toHex(c) + ";");
        } else {
            strengthLbl.setText("");
        }
    }

    private String toHex(Color c) {
        return String.format("#%02X%02X%02X",
            (int) (c.getRed() * 255), (int) (c.getGreen() * 255), (int) (c.getBlue() * 255));
    }

    private void updateRules(String p) {
        if (p == null) p = "";
        applyRule(r1, p.length() >= 12, "12+ characters");
        applyRule(r2, p.matches(".*[A-Z].*") && p.matches(".*[a-z].*"), "Upper and lower case");
        applyRule(r3, p.matches(".*[0-9].*"), "Contains a number");
        applyRule(r4, p.matches(".*[^A-Za-z0-9].*"), "Contains a special character");
    }

    private void applyRule(Label lbl, boolean ok, String rule) {
        if (lbl == null) return;
        lbl.setText((ok ? "✓ " : "○ ") + rule);
        lbl.setStyle("-fx-text-fill:" + (ok ? "#2E6B0A" : "#C5CDD8") + ";");
    }

    private void updateMatch() {
        String p = pwdField.getText(), c = confirmField.getText();
        if (c.isEmpty()) { matchLbl.setText(""); return; }
        if (p.equals(c)) { matchLbl.setText("✓ Passwords match"); matchLbl.setStyle("-fx-text-fill:#2E6B0A;"); }
        else { matchLbl.setText("✗ Passwords do not match"); matchLbl.setStyle("-fx-text-fill:#A32D2D;"); }
    }

    private boolean meetsReqs(String p) {
        return p.length() >= 12 && p.matches(".*[A-Z].*") && p.matches(".*[a-z].*")
            && p.matches(".*[0-9].*") && p.matches(".*[^A-Za-z0-9].*");
    }

    private boolean swap(PasswordField pf, TextField tf, boolean open, Button btn) {
        open = !open;
        if (open) {
            tf.setText(pf.getText()); tf.setVisible(true); tf.setManaged(true);
            pf.setVisible(false); pf.setManaged(false); btn.setText("HIDE");
            Platform.runLater(() -> { tf.requestFocus(); tf.positionCaret(tf.getText().length()); });
        } else {
            pf.setText(tf.getText()); pf.setVisible(true); pf.setManaged(true);
            tf.setVisible(false); tf.setManaged(false); btn.setText("SHOW");
            Platform.runLater(() -> { pf.requestFocus(); pf.positionCaret(pf.getText().length()); });
        }
        return open;
    }
}
