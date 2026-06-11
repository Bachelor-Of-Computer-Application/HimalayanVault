package com.himalayanvault.ui;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

import javax.crypto.SecretKey;

import com.himalayanvault.db.DatabaseManager;
import com.himalayanvault.export.VaultExporter;
import com.himalayanvault.export.VaultImporter;
import com.himalayanvault.models.Credential;
import com.himalayanvault.security.ClipboardProtector;
import com.himalayanvault.security.EncryptionUtil;
import com.himalayanvault.security.SessionManager;
import com.himalayanvault.security.SessionManager.SessionData;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * VaultController — main vault screen with credential management.
 */
public class VaultController implements Initializable {

    public static final double VAULT_WIDTH = 900;
    public static final double VAULT_HEIGHT = 720;

    private static final double AUTH_WIDTH = 1024;
    private static final double AUTH_HEIGHT = 640;
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());

    @FXML private BorderPane root;
    @FXML private Label vaultStatus;
    @FXML private Label credentialCountLabel;
    @FXML private Label detailLabel;
    @FXML private TextField searchField;
    @FXML private TableView<Credential> credentialTable;
    @FXML private TableColumn<Credential, String> siteColumn;
    @FXML private TableColumn<Credential, String> usernameColumn;
    @FXML private TableColumn<Credential, String> urlColumn;
    @FXML private TableColumn<Credential, String> notesColumn;
    @FXML private TableColumn<Credential, String> updatedColumn;
    @FXML private Button editButton;
    @FXML private Button deleteButton;
    @FXML private Button copyUserButton;
    @FXML private Button copyPassButton;

    private String sessionToken;
    private String currentUsername;
    private final List<Credential> credentials = new ArrayList<>();
    private final ObservableList<Credential> tableItems = FXCollections.observableArrayList();
    private FilteredList<Credential> filteredItems;

    public void setSession(String token) {
        this.sessionToken = token;
        SessionData session = SessionManager.getInstance().validateSession(token);
        if (session != null) {
            this.currentUsername = session.username;
            refreshCredentials();
        }
    }

    public void setUsername(String username) {
        this.currentUsername = username;
        System.out.println("[VaultController] Vault opened for user: " + username);
        updateStatusLabel();
        refreshCredentials();
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTable();
        setupSearch();
        setupSelection();
        System.out.println("[VaultController] Vault screen initialized");
    }

    public static void configureVaultStage(Stage stage) {
        stage.setResizable(false);
        stage.setMinWidth(VAULT_WIDTH);
        stage.setMinHeight(VAULT_HEIGHT);
        stage.setMaxWidth(VAULT_WIDTH);
        stage.setMaxHeight(VAULT_HEIGHT);
    }

    public static void configureAuthStage(Stage stage) {
        stage.setResizable(false);
        stage.setMinWidth(AUTH_WIDTH);
        stage.setMinHeight(AUTH_HEIGHT);
        stage.setMaxWidth(AUTH_WIDTH);
        stage.setMaxHeight(AUTH_HEIGHT);
    }

    private void setupTable() {
        siteColumn.setCellValueFactory(data ->
                new SimpleStringProperty(displayValue(data.getValue().siteName, data.getValue().siteUrl)));
        usernameColumn.setCellValueFactory(data ->
                new SimpleStringProperty(displayValue(data.getValue().siteUsername)));
        urlColumn.setCellValueFactory(data ->
                new SimpleStringProperty(displayValue(data.getValue().siteUrl)));
        notesColumn.setCellValueFactory(data ->
                new SimpleStringProperty(truncate(displayValue(data.getValue().notes), 40)));
        updatedColumn.setCellValueFactory(data ->
                new SimpleStringProperty(formatUpdatedAt(data.getValue().updated_at)));

        filteredItems = new FilteredList<>(tableItems, cred -> true);
        credentialTable.setItems(filteredItems);
    }

    private void setupSearch() {
        searchField.textProperty().addListener((obs, oldValue, query) -> {
            String needle = query == null ? "" : query.trim().toLowerCase();
            filteredItems.setPredicate(cred -> {
                if (needle.isEmpty()) {
                    return true;
                }
                return containsIgnoreCase(cred.siteName, needle)
                        || containsIgnoreCase(cred.siteUrl, needle)
                        || containsIgnoreCase(cred.siteUsername, needle)
                        || containsIgnoreCase(cred.notes, needle);
            });
        });
    }

    private void setupSelection() {
        credentialTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, selected) -> {
            boolean hasSelection = selected != null;
            editButton.setDisable(!hasSelection);
            deleteButton.setDisable(!hasSelection);
            copyUserButton.setDisable(!hasSelection);
            copyPassButton.setDisable(!hasSelection);

            if (selected == null) {
                detailLabel.setText("Select a credential to view details.");
                return;
            }

            detailLabel.setText(String.format(
                    "%s  •  %s  •  %s%s",
                    displayValue(selected.siteName, selected.siteUrl),
                    displayValue(selected.siteUsername),
                    displayValue(selected.siteUrl),
                    selected.notes == null || selected.notes.isBlank()
                            ? ""
                            : "  •  Notes: " + selected.notes));
        });
    }

    @FXML
    private void handleRefresh() {
        refreshCredentials();
    }

    @FXML
    private void handleAdd() {
        if (!ensureLoggedIn()) {
            return;
        }

        Optional<CredentialForm> form = showCredentialDialog(null);
        if (form.isEmpty()) {
            return;
        }

        CredentialForm data = form.get();
        if (data.password.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Add Credential", "Password is required.");
            return;
        }

        try {
            String encrypted = encryptForStorage(data.password);
            long id = DatabaseManager.getInstance().saveCredential(
                    currentUsername,
                    data.siteUrl,
                    data.siteName,
                    data.siteUsername,
                    1,
                    encrypted,
                    data.notes);

            if (id > 0) {
                refreshCredentials();
                selectCredentialById(id);
                showAlert(Alert.AlertType.INFORMATION, "Saved", "Credential added successfully.");
            } else {
                showAlert(Alert.AlertType.ERROR, "Add Credential", "Failed to save credential.");
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Add Credential", e.getMessage());
        }
    }

    @FXML
    private void handleEdit() {
        Credential selected = credentialTable.getSelectionModel().getSelectedItem();
        if (selected == null || !ensureLoggedIn()) {
            return;
        }

        Optional<CredentialForm> form = showCredentialDialog(selected);
        if (form.isEmpty()) {
            return;
        }

        CredentialForm data = form.get();
        try {
            String encrypted = data.password.isBlank()
                    ? selected.encryptedPassword
                    : encryptForStorage(data.password);

            boolean updated = DatabaseManager.getInstance().updateCredential(
                    selected.id,
                    currentUsername,
                    data.siteUrl,
                    data.siteName,
                    data.siteUsername,
                    encrypted,
                    data.notes);

            if (updated) {
                refreshCredentials();
                selectCredentialById(selected.id);
                showAlert(Alert.AlertType.INFORMATION, "Updated", "Credential updated successfully.");
            } else {
                showAlert(Alert.AlertType.ERROR, "Edit Credential", "Failed to update credential.");
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Edit Credential", e.getMessage());
        }
    }

    @FXML
    private void handleDelete() {
        Credential selected = credentialTable.getSelectionModel().getSelectedItem();
        if (selected == null || !ensureLoggedIn()) {
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete credential for " + displayValue(selected.siteName, selected.siteUrl)
                        + " (" + selected.siteUsername + ")?",
                ButtonType.CANCEL, ButtonType.OK);
        confirm.setTitle("Delete Credential");
        confirm.setHeaderText(null);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        boolean deleted = DatabaseManager.getInstance().deleteCredential(selected.id, currentUsername);
        if (deleted) {
            refreshCredentials();
            showAlert(Alert.AlertType.INFORMATION, "Deleted", "Credential removed.");
        } else {
            showAlert(Alert.AlertType.ERROR, "Delete Credential", "Failed to delete credential.");
        }
    }

    @FXML
    private void handleCopyUsername() {
        Credential selected = credentialTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        ClipboardProtector.copySensitiveText(selected.siteUsername);
    }

    @FXML
    private void handleCopyPassword() {
        Credential selected = credentialTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        try {
            String password = decryptForDisplay(selected.encryptedPassword);
            if (password.isBlank()) {
                showAlert(Alert.AlertType.WARNING, "Copy Password", "No password available for this entry.");
                return;
            }
            ClipboardProtector.copySensitiveText(password);
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Copy Password", e.getMessage());
        }
    }

    @FXML
    private void handleExport() {
        if (!ensureLoggedIn()) {
            return;
        }

        TextInputDialog passphraseDialog = new TextInputDialog();
        passphraseDialog.setTitle("Export Vault");
        passphraseDialog.setHeaderText("Enter an export passphrase");
        passphraseDialog.setContentText("Passphrase:");

        Optional<String> passphraseResult = passphraseDialog.showAndWait();
        if (passphraseResult.isEmpty() || passphraseResult.get().isBlank()) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Vault Export");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Himalayan Vault Export", "*.hvlt"));
        chooser.setInitialFileName("himalayan-vault-export.hvlt");

        java.io.File file = chooser.showSaveDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }

        try {
            VaultExporter exporter = new VaultExporter();
            VaultExporter.ExportResult result = exporter.exportWithPassphrase(
                    credentials,
                    passphraseResult.get(),
                    "vault-export-" + currentUsername);

            byte[] bytes = Base64.getDecoder().decode(result.encryptedFile);
            Files.write(file.toPath(), bytes);

            showAlert(Alert.AlertType.INFORMATION, "Export Complete",
                    "Exported " + credentials.size() + " credential(s) to:\n" + file.getAbsolutePath());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Export Failed", e.getMessage());
        }
    }

    @FXML
    private void handleImport() {
        if (!ensureLoggedIn()) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Vault Export");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Himalayan Vault Export", "*.hvlt", "*.*"));

        java.io.File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }

        TextInputDialog keyDialog = new TextInputDialog();
        keyDialog.setTitle("Import Vault");
        keyDialog.setHeaderText("Enter the export passphrase or 12-word mnemonic");
        keyDialog.setContentText("Key:");

        Optional<String> keyResult = keyDialog.showAndWait();
        if (keyResult.isEmpty() || keyResult.get().isBlank()) {
            return;
        }

        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String encryptedFile = Base64.getEncoder().encodeToString(bytes);
            String key = keyResult.get().trim();

            VaultImporter importer = new VaultImporter();
            VaultImporter.MergeResult mergeResult;

            if (key.split("\\s+").length == 12) {
                mergeResult = importer.importWithMnemonic(encryptedFile, key, credentials);
            } else {
                mergeResult = importer.importWithPassphrase(encryptedFile, key, credentials);
            }

            DatabaseManager db = DatabaseManager.getInstance();
            for (Credential credential : mergeResult.mergedCredentials) {
                credential.ownerUsername = currentUsername;
                db.upsertCredentialByUrlAndUsername(currentUsername, credential);
            }

            refreshCredentials();
            showAlert(Alert.AlertType.INFORMATION, "Import Complete",
                    "Added " + mergeResult.credentialsAdded + ", updated " + mergeResult.credentialsUpdated
                            + " credential(s).");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Import Failed", e.getMessage());
        }
    }

    @FXML
    private void handleLock() {
        if (sessionToken != null) {
            SessionManager.getInstance().invalidateSession(sessionToken);
            sessionToken = null;
        } else {
            SessionManager.getInstance().lock();
        }
        try {
            Parent login = FXMLLoader.load(getClass().getResource("/fxml/login.fxml"));
            Stage stage = (Stage) root.getScene().getWindow();
            Scene scene = new Scene(login, AUTH_WIDTH, AUTH_HEIGHT);
            scene.getStylesheets().add(getClass().getResource("/css/login.css").toExternalForm());
            stage.setScene(scene);
            configureAuthStage(stage);
            stage.centerOnScreen();
        } catch (IOException e) {
            System.err.println("[VaultController] Failed to lock vault: " + e.getMessage());
        }
    }

    private void refreshCredentials() {
        if (currentUsername == null) {
            return;
        }

        credentials.clear();
        try {
            ResultSet rs = DatabaseManager.getInstance().loadCredentialsForUser(currentUsername);
            while (rs != null && rs.next()) {
                credentials.add(resultSetToCredential(rs));
            }
            if (rs != null) {
                rs.close();
            }
        } catch (Exception e) {
            System.err.println("[VaultController] Failed to load credentials: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Load Credentials", "Could not load credentials: " + e.getMessage());
        }

        tableItems.setAll(credentials);
        if (credentialCountLabel != null) {
            int count = credentials.size();
            credentialCountLabel.setText(count + " stored credential" + (count == 1 ? "" : "s"));
        }
        updateStatusLabel();
    }

    private void selectCredentialById(long id) {
        for (Credential credential : filteredItems) {
            if (credential.id == id) {
                credentialTable.getSelectionModel().select(credential);
                credentialTable.scrollTo(credential);
                return;
            }
        }
    }

    private Optional<CredentialForm> showCredentialDialog(Credential existing) {
        Dialog<CredentialForm> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add Credential" : "Edit Credential");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        TextField siteUrlField = new TextField();
        siteUrlField.setPromptText("https://example.com");
        siteUrlField.getStyleClass().add("dialog-field");

        TextField siteNameField = new TextField();
        siteNameField.setPromptText("Example");
        siteNameField.getStyleClass().add("dialog-field");

        TextField usernameField = new TextField();
        usernameField.getStyleClass().add("dialog-field");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText(existing == null ? "Required" : "Leave blank to keep current");
        passwordField.getStyleClass().add("dialog-field");

        TextArea notesArea = new TextArea();
        notesArea.setPrefRowCount(3);
        notesArea.getStyleClass().add("dialog-field");

        if (existing != null) {
            siteUrlField.setText(existing.siteUrl);
            siteNameField.setText(existing.siteName);
            usernameField.setText(existing.siteUsername);
            notesArea.setText(existing.notes == null ? "" : existing.notes);
        }

        GridPane grid = new GridPane();
        grid.getStyleClass().add("dialog-grid");
        grid.setPadding(new Insets(12, 16, 8, 16));
        grid.add(makeDialogLabel("Site URL"), 0, 0);
        grid.add(siteUrlField, 1, 0);
        grid.add(makeDialogLabel("Site Name"), 0, 1);
        grid.add(siteNameField, 1, 1);
        grid.add(makeDialogLabel("Username"), 0, 2);
        grid.add(usernameField, 1, 2);
        grid.add(makeDialogLabel("Password"), 0, 3);
        grid.add(passwordField, 1, 3);
        grid.add(makeDialogLabel("Notes"), 0, 4);
        grid.add(notesArea, 1, 4);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/css/vault.css").toExternalForm());

        Platform.runLater(siteUrlField::requestFocus);

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }

            String siteUrl = siteUrlField.getText().trim();
            String siteName = siteNameField.getText().trim();
            String siteUsername = usernameField.getText().trim();
            String password = passwordField.getText();
            String notes = notesArea.getText().trim();

            if (siteUrl.isBlank() || siteUsername.isBlank()) {
                showAlert(Alert.AlertType.WARNING, "Credential", "Site URL and username are required.");
                return null;
            }
            if (siteName.isBlank()) {
                siteName = siteUrl;
            }

            return new CredentialForm(siteUrl, siteName, siteUsername, password, notes);
        });

        return dialog.showAndWait();
    }

    private Label makeDialogLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("dialog-label");
        return label;
    }

    private boolean ensureLoggedIn() {
        if (currentUsername == null) {
            showAlert(Alert.AlertType.WARNING, "Vault", "No active vault session. Please log in again.");
            return false;
        }
        return true;
    }

    private void updateStatusLabel() {
        if (vaultStatus != null && currentUsername != null) {
            vaultStatus.setText("Unlocked — " + currentUsername);
        }
    }

    private String encryptForStorage(String plaintext) {
        SecretKey key = getEncryptionKey();
        if (key == null || plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }
        if (looksEncrypted(plaintext)) {
            return plaintext;
        }
        return EncryptionUtil.encrypt(plaintext, key);
    }

    private String decryptForDisplay(String stored) {
        if (stored == null || stored.isBlank()) {
            return "";
        }
        if (!looksEncrypted(stored)) {
            return stored;
        }

        SecretKey key = getEncryptionKey();
        if (key == null) {
            throw new IllegalStateException("Unlock the vault with your master password to view saved passwords.");
        }
        try {
            return EncryptionUtil.decrypt(stored, key);
        } catch (RuntimeException e) {
            return "[unable to decrypt — re-save this credential]";
        }
    }

    private SecretKey getEncryptionKey() {
        if (sessionToken == null) {
            return null;
        }
        return SessionManager.getInstance().getEncryptionKey(sessionToken);
    }

    private boolean looksEncrypted(String value) {
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            return decoded.length > 12;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Credential resultSetToCredential(ResultSet rs) throws Exception {
        long createdAtMs = readTimestamp(rs, "created_at");
        long updatedAtMs = readTimestamp(rs, "updated_at");

        int accountNumber = 1;
        try {
            accountNumber = rs.getInt("account_number");
            if (rs.wasNull()) {
                accountNumber = 1;
            }
        } catch (Exception ignored) {
            accountNumber = 1;
        }

        return new Credential(
                rs.getLong("id"),
                rs.getString("owner_username"),
                rs.getString("site_url"),
                rs.getString("site_name"),
                rs.getString("site_username"),
                accountNumber,
                rs.getString("encrypted_password"),
                rs.getString("notes"),
                createdAtMs,
                updatedAtMs);
    }

    private long readTimestamp(ResultSet rs, String column) {
        try {
            long value = rs.getLong(column);
            if (!rs.wasNull() && value > 0) {
                return value;
            }
        } catch (Exception ignored) {
            // fall through to string parsing
        }

        try {
            String text = rs.getString(column);
            if (text != null && !text.isBlank()) {
                return java.sql.Timestamp.valueOf(text).getTime();
            }
        } catch (Exception ignored) {
            // ignore
        }

        return System.currentTimeMillis();
    }

    private String formatUpdatedAt(long updatedAtMs) {
        if (updatedAtMs <= 0) {
            return "—";
        }
        return DATE_FMT.format(Instant.ofEpochMilli(updatedAtMs));
    }

    private String displayValue(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value == null ? "" : value;
        }
        return value.substring(0, max - 1) + "…";
    }

    private boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type, message, ButtonType.OK);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.showAndWait();
        });
    }

    private record CredentialForm(String siteUrl, String siteName, String siteUsername, String password, String notes) {
    }
}
