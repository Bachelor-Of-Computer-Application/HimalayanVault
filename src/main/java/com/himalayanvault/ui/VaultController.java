package com.himalayanvault.ui;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.TreeSet;   

import javax.crypto.SecretKey;

import com.himalayanvault.auth.AuthManager;
import com.himalayanvault.auth.BiometricHandler;
import com.himalayanvault.db.DatabaseManager;
import com.himalayanvault.export.CsvCredentialTransfer;
import com.himalayanvault.export.VaultExporter;
import com.himalayanvault.export.VaultImporter;
import com.himalayanvault.models.Credential;
import com.himalayanvault.security.ClipboardProtector;
import com.himalayanvault.security.CredentialKeyDerivation;
import com.himalayanvault.security.EncryptionUtil;
import com.himalayanvault.security.PasswordBreachChecker;
import com.himalayanvault.security.PasswordStrength;
import com.himalayanvault.security.Pbkdf2PasswordHasher;
import com.himalayanvault.security.SessionManager;
import com.himalayanvault.security.SessionManager.SessionData;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

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
    private static final long MIN_VALID_TIMESTAMP_MS =
            LocalDate.of(2000, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    static final String HVLT_IMPORT_DESCRIPTION = "Encrypted Himalayan Vault backup (*.hvlt)";
    static final String CSV_IMPORT_DESCRIPTION = "Password manager CSV (*.csv)";

    @FXML private BorderPane root;
    @FXML private Label vaultStatus;
    @FXML private Label credentialCountLabel;
    @FXML private Label detailLabel;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> categoryFilter;
    @FXML private TableView<Credential> credentialTable;
    @FXML private TableColumn<Credential, String> favoriteColumn;
    @FXML private TableColumn<Credential, String> siteColumn;
    @FXML private TableColumn<Credential, String> usernameColumn;
    @FXML private TableColumn<Credential, String> categoryColumn;
    @FXML private TableColumn<Credential, String> tagsColumn;
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
    private final AuthManager authManager = new AuthManager();
    private final BiometricHandler biometricHandler = new BiometricHandler();
    private final PasswordBreachChecker breachChecker = new PasswordBreachChecker();
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
        favoriteColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().favorite ? "★" : ""));
        siteColumn.setCellValueFactory(data ->
                new SimpleStringProperty(displayValue(data.getValue().siteName, data.getValue().siteUrl)));
        siteColumn.setCellFactory(column -> new TableCell<>() {
            private final ImageView favicon = new ImageView();
            private final Label label = new Label();
            private final HBox box = new HBox(8, favicon, label);

            {
                favicon.setFitWidth(16);
                favicon.setFitHeight(16);
                favicon.setPreserveRatio(true);
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(String site, boolean empty) {
                super.updateItem(site, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                Credential credential = getTableRow().getItem();
                String faviconUrl = faviconUrlFor(credential.siteUrl);
                label.setText(site);
                favicon.setImage(faviconUrl == null ? null : new Image(faviconUrl, true));
                favicon.setVisible(faviconUrl != null);
                favicon.setManaged(faviconUrl != null);
                setGraphic(box);
                setText(null);
            }
        });
        usernameColumn.setCellValueFactory(data ->
                new SimpleStringProperty(displayValue(data.getValue().siteUsername)));
        categoryColumn.setCellValueFactory(data ->
                new SimpleStringProperty(displayValue(data.getValue().category)));
        tagsColumn.setCellValueFactory(data ->
                new SimpleStringProperty(displayValue(data.getValue().tags)));
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
        searchField.textProperty().addListener((obs, oldValue, query) -> applyFilters());
        categoryFilter.getItems().setAll("All categories", "Favourites", "Recent");
        categoryFilter.getSelectionModel().selectFirst();
        categoryFilter.valueProperty().addListener((obs, oldValue, category) -> applyFilters());
    }

    private void applyFilters() {
        String needle = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        String selectedCategory = categoryFilter.getValue();

        filteredItems.setPredicate(cred -> {
            boolean categoryMatches = selectedCategory == null
                    || selectedCategory.equals("All categories")
                    || (selectedCategory.equals("Favourites") && cred.favorite)
                    || (selectedCategory.equals("Recent") && isRecent(cred))
                    || selectedCategory.equals(displayValue(cred.category));
            if (!categoryMatches) {
                return false;
            }
            if (needle.isEmpty()) {
                return true;
            }
            return containsIgnoreCase(cred.siteName, needle)
                    || containsIgnoreCase(cred.siteUrl, needle)
                    || containsIgnoreCase(cred.siteUsername, needle)
                    || containsIgnoreCase(cred.notes, needle)
                    || containsIgnoreCase(cred.category, needle)
                    || containsIgnoreCase(cred.tags, needle);
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
                    "%s%s  •  %s  •  %s%s%s%s",
                    selected.favorite ? "★ " : "",
                    displayValue(selected.siteName, selected.siteUrl),
                    displayValue(selected.siteUsername),
                    displayValue(selected.siteUrl),
                    selected.category == null || selected.category.isBlank()
                            ? ""
                            : "  •  Category: " + selected.category,
                    selected.tags == null || selected.tags.isBlank()
                            ? ""
                            : "  •  Tags: " + selected.tags,
                    selected.notes == null || selected.notes.isBlank()
                            ? ""
                            : "  •  Notes: " + selected.notes));
        });
        credentialTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2
                    && credentialTable.getSelectionModel().getSelectedItem() != null) {
                handleEdit();
            }
        });
    }

    @FXML
    private void handleRefresh() {
        refreshCredentials();
    }

    @FXML
    private void handleSecurityDashboard() {
        if (!ensureLoggedIn()) {
            return;
        }
        long favorites = credentials.stream().filter(credential -> credential.favorite).count();
        long recent = credentials.stream().filter(this::isRecent).count();
        showAlert(Alert.AlertType.INFORMATION, "Security Dashboard",
                "Stored credentials: " + credentials.size()
                        + "\nFavorites: " + favorites
                        + "\nRecently updated: " + recent
                        + "\nUse the category filter to view these groups.");
    }

    @FXML
    private void handleChangeMasterPassword() {
        if (!ensureLoggedIn() || isLockedOut()) {
            return;
        }

        Optional<PasswordChangeForm> form = showPasswordChangeDialog();
        if (form.isEmpty()) {
            return;
        }

        PasswordChangeForm data = form.get();
        if (!data.newPassword.equals(data.confirmPassword)) {
            showAlert(Alert.AlertType.WARNING, "Change Master Password", "New passwords do not match.");
            return;
        }
        if (!PasswordStrength.meetsMasterPasswordRequirements(data.newPassword)) {
            showAlert(Alert.AlertType.WARNING, "Change Master Password",
                    "New master password must be at least 12 characters and include uppercase, lowercase, number, and symbol.");
            return;
        }

        AuthManager.VerificationResult result =
                authManager.verifyMasterPassword(currentUsername, data.currentPassword);
        if (result != AuthManager.VerificationResult.SUCCESS) {
            showAlert(Alert.AlertType.ERROR, "Change Master Password", "Current master password is incorrect.");
            return;
        }

        try {
            SecretKey oldKey = CredentialKeyDerivation.keyForUser(currentUsername, data.currentPassword);
            SecretKey newKey = CredentialKeyDerivation.keyForUser(currentUsername, data.newPassword);
            List<Credential> reencrypted = new ArrayList<>();
            for (Credential credential : credentials) {
                Credential copy = copyCredential(credential);
                if (looksEncrypted(credential.encryptedPassword)) {
                    String plaintext = EncryptionUtil.decrypt(credential.encryptedPassword, oldKey);
                    copy.encryptedPassword = EncryptionUtil.encrypt(plaintext, newKey);
                }
                reencrypted.add(copy);
            }

            String newHash = Pbkdf2PasswordHasher.hashPassword(data.newPassword);
            DatabaseManager.getInstance().rotateMasterPasswordAndCredentials(
                    currentUsername, newHash, reencrypted);

            SessionManager.getInstance().invalidateAllSessions();
            sessionToken = SessionManager.getInstance().createSession(
                    currentUsername, data.newPassword, CredentialKeyDerivation.saltForUser(currentUsername));
            refreshCredentials();
            showAlert(Alert.AlertType.INFORMATION, "Change Master Password",
                    "Master password changed and vault data re-encrypted successfully.");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Change Master Password", e.getMessage());
        }
    }

    @FXML
    private void handleRecoveryCodes() {
        showAlert(Alert.AlertType.INFORMATION, "Recovery Codes",
                "Recovery words remain valid after changing your master password.");
    }

    @FXML
    private void handleBiometricSettings() {
        if (!ensureLoggedIn() || isLockedOut()) {
            return;
        }

        boolean enabled = DatabaseManager.getInstance().isBiometricEnabled(currentUsername);
        Dialog<BiometricSettingForm> dialog = new Dialog<>();
        dialog.setTitle("Biometric Settings");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        Label statusLabel = new Label(enabled ? "Biometric unlock is enabled." : "Biometric unlock is disabled.");
        statusLabel.getStyleClass().add("dialog-status");

        CheckBox enableBox = new CheckBox("Enable biometric unlock after master-password login");
        enableBox.setSelected(enabled);
        enableBox.getStyleClass().add("dialog-check");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Confirm current master password");
        passwordField.getStyleClass().add("dialog-field");

        Label supportLabel = new Label(biometricHandler.isAvailable()
                ? "This device reports biometric support."
                : "This build does not expose an OS biometric provider yet.");
        supportLabel.getStyleClass().add("helper-text");
        supportLabel.setWrapText(true);

        VBox content = new VBox(10,
                statusLabel,
                enableBox,
                makeDialogLabel("Master Password"),
                passwordField,
                supportLabel);
        content.setPadding(new Insets(12, 16, 8, 16));
        content.setPrefWidth(360);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/css/vault.css").toExternalForm());

        Platform.runLater(passwordField::requestFocus);
        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            return new BiometricSettingForm(enableBox.isSelected(), passwordField.getText());
        });

        Optional<BiometricSettingForm> form = dialog.showAndWait();
        if (form.isEmpty()) {
            return;
        }

        BiometricSettingForm data = form.get();
        if (data.masterPassword() == null || data.masterPassword().isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Biometric Settings", "Enter your current master password to change biometric settings.");
            return;
        }

        AuthManager.VerificationResult result =
                authManager.verifyMasterPassword(currentUsername, data.masterPassword());
        if (result != AuthManager.VerificationResult.SUCCESS) {
            showAlert(Alert.AlertType.ERROR, "Biometric Settings", "Current master password is incorrect.");
            return;
        }

        boolean updated = DatabaseManager.getInstance().setBiometricEnabled(currentUsername, data.enabled());
        if (!updated) {
            showAlert(Alert.AlertType.ERROR, "Biometric Settings", "Could not update biometric settings.");
            return;
        }

        showAlert(Alert.AlertType.INFORMATION, "Biometric Settings",
                data.enabled()
                        ? "Biometric unlock enabled. Log in with your master password first; biometric unlock can be used only when this build has an OS biometric provider."
                        : "Biometric unlock disabled.");
    }

    @FXML
    private void handleSettings() {
        showAlert(Alert.AlertType.INFORMATION, "Settings",
                "Auto-lock, import, export, search, favorites, and recent filters use the current vault defaults.");
    }

    @FXML
    private void handleAbout() {
        showAlert(Alert.AlertType.INFORMATION, "About",
                "Himalayan Vault\nLocal encrypted password vault.");
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
                    data.notes,
                    data.category,
                    data.tags,
                    data.favorite);

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
                    data.notes,
                    data.category,
                    data.tags,
                    data.favorite);

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
        if (!ensureLoggedIn() || isLockedOut()) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Vault Export");
        FileChooser.ExtensionFilter hvltFilter =
                new FileChooser.ExtensionFilter("Encrypted Himalayan Vault backup (*.hvlt)", "*.hvlt");
        FileChooser.ExtensionFilter csvFilter =
                new FileChooser.ExtensionFilter("Password manager CSV (*.csv)", "*.csv");
        chooser.getExtensionFilters().addAll(hvltFilter, csvFilter);
        chooser.setSelectedExtensionFilter(hvltFilter);
        chooser.setInitialFileName("himalayan-vault-export.hvlt");

        java.io.File file = chooser.showSaveDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }

        try {
            if (isCsvExport(chooser, file)) {
    ChoiceDialog<CsvCredentialTransfer.CsvFormat> formatDialog = new ChoiceDialog<>(
            CsvCredentialTransfer.CsvFormat.GENERIC,
            CsvCredentialTransfer.CsvFormat.values());
    formatDialog.setTitle("CSV Export Format");
    formatDialog.setHeaderText("Which password manager are you exporting to?");
    formatDialog.setContentText("Format:");
    Optional<CsvCredentialTransfer.CsvFormat> fmt = formatDialog.showAndWait();
    if (fmt.isEmpty()) return;

    Alert warn = new Alert(Alert.AlertType.WARNING,
            "This file will contain all your passwords in plain text.\n" +
            "Delete it after importing and do not store it in cloud storage.",
            ButtonType.OK, ButtonType.CANCEL);
    warn.setTitle("Plaintext Export Warning");
    if (warn.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

    CsvCredentialTransfer csv = new CsvCredentialTransfer();
    String csvText = csv.exportCsv(
            credentials,
            credential -> decryptForDisplay(credential.encryptedPassword),
            fmt.get());
    Files.writeString(file.toPath(), csvText, StandardCharsets.UTF_8);
    showAlert(Alert.AlertType.INFORMATION, "CSV Export Complete",
            "Exported " + credentials.size() + " credential(s) to " + fmt.get() + " CSV:\n"
            + file.getAbsolutePath());
}
            else {
                TextInputDialog passphraseDialog = new TextInputDialog();
                passphraseDialog.setTitle("Export Vault");
                passphraseDialog.setHeaderText("Enter an export passphrase");
                passphraseDialog.setContentText("Passphrase:");

                Optional<String> passphraseResult = passphraseDialog.showAndWait();
                if (passphraseResult.isEmpty() || passphraseResult.get().isBlank()) {
                    return;
                }

                VaultExporter exporter = new VaultExporter();
                VaultExporter.ExportResult result = exporter.exportWithPassphrase(
                        credentials,
                        passphraseResult.get(),
                        "vault-export-" + currentUsername);

                byte[] bytes = Base64.getDecoder().decode(result.encryptedFile);
                Files.write(file.toPath(), bytes);

                showAlert(Alert.AlertType.INFORMATION, "Export Complete",
                        "Exported " + credentials.size() + " credential(s) to encrypted backup:\n"
                                + file.getAbsolutePath());
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Export Failed", e.getMessage());
        }
    }

    @FXML
    private void handleImport() {
        if (!ensureLoggedIn() || isLockedOut()) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Vault Data");
        chooser.getExtensionFilters().addAll(importExtensionFilters());

        java.io.File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }

        if (file.getName().toLowerCase().endsWith(".csv")) {
            importCsv(file);
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
            authManager.recordAuthenticationFailure(currentUsername);
            if (authManager.isLockedOut(currentUsername)) {
                showAlert(Alert.AlertType.ERROR, "Import Failed", authManager.lockoutMessage(currentUsername));
            } else {
                showAlert(Alert.AlertType.ERROR, "Import Failed",
                        e.getMessage() + "\n" + authManager.failureMessage(currentUsername));
            }
        }
    }

    private boolean isCsvExport(FileChooser chooser, java.io.File file) {
        FileChooser.ExtensionFilter selected = chooser.getSelectedExtensionFilter();
        return file.getName().toLowerCase().endsWith(".csv")
                || (selected != null && selected.getDescription().toLowerCase().contains("csv"));
    }

    static List<FileChooser.ExtensionFilter> importExtensionFilters() {
        return List.of(
                new FileChooser.ExtensionFilter("Supported imports (*.hvlt, *.csv)", "*.hvlt", "*.csv"),
                new FileChooser.ExtensionFilter(HVLT_IMPORT_DESCRIPTION, "*.hvlt"),
                new FileChooser.ExtensionFilter(CSV_IMPORT_DESCRIPTION, "*.csv"));
    }

    private void importCsv(java.io.File file) {
        try {
            CsvCredentialTransfer csv = new CsvCredentialTransfer();
            List<Credential> imported = csv.importCsv(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            DatabaseManager db = DatabaseManager.getInstance();
            int added = 0;
            int updated = 0;

            for (Credential credential : imported) {
                if (credential.siteUrl.isBlank()) {
                    credential.siteUrl = credential.siteName;
                }
                if (credential.siteName.isBlank()) {
                    credential.siteName = credential.siteUrl;
                }
                if (credential.siteUsername.isBlank() || credential.encryptedPassword.isBlank()) {
                    continue;
                }

                boolean existed = db.credentialExists(currentUsername, credential.siteUrl, credential.siteUsername);
                credential.ownerUsername = currentUsername;
                credential.encryptedPassword = encryptForStorage(credential.encryptedPassword);
                if (db.upsertCredentialByUrlAndUsername(currentUsername, credential)) {
                    if (existed) {
                        updated++;
                    } else {
                        added++;
                    }
                }
            }

            refreshCredentials();
            showAlert(Alert.AlertType.INFORMATION, "CSV Import Complete",
                    "Added " + added + ", updated " + updated + " credential(s).");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "CSV Import Failed", e.getMessage());
        }
    }

    private boolean isLockedOut() {
        if (authManager.isLockedOut(currentUsername)) {
            showAlert(Alert.AlertType.WARNING, "Temporarily Locked", authManager.lockoutMessage(currentUsername));
            return true;
        }
        return false;
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
        refreshCategoryFilter();
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

        TextField categoryField = new TextField();
        categoryField.setPromptText("Banking, Email, Social");
        categoryField.getStyleClass().add("dialog-field");

        TextField tagsField = new TextField();
        tagsField.setPromptText("work, shared, critical");
        tagsField.getStyleClass().add("dialog-field");

        CheckBox favoriteBox = new CheckBox("Favourite");
        favoriteBox.getStyleClass().add("dialog-check");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText(existing == null ? "Required" : "Leave blank to keep current");
        passwordField.getStyleClass().add("dialog-field");

        Rectangle seg1 = new Rectangle(36, 6);
        Rectangle seg2 = new Rectangle(36, 6);
        Rectangle seg3 = new Rectangle(36, 6);
        Rectangle seg4 = new Rectangle(36, 6);
        Label strengthLabel = new Label();
        strengthLabel.getStyleClass().add("helper-text");
        PasswordStrengthMeter strengthMeter = new PasswordStrengthMeter(seg1, seg2, seg3, seg4, strengthLabel);
        Label breachLabel = new Label();
        breachLabel.getStyleClass().add("helper-text");
        PauseTransition breachDelay = new PauseTransition(Duration.millis(450));
        final int[] breachRequest = {0};
        passwordField.textProperty().addListener((obs, old, value) -> {
            strengthMeter.update(value);
            scheduleBreachCheck(value, breachLabel, breachDelay, breachRequest);
        });

        HBox strengthRow = new HBox(4, seg1, seg2, seg3, seg4);
        strengthRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        TextArea notesArea = new TextArea();
        notesArea.setPrefRowCount(3);
        notesArea.getStyleClass().add("dialog-field");

        if (existing != null) {
            siteUrlField.setText(existing.siteUrl);
            siteNameField.setText(existing.siteName);
            usernameField.setText(existing.siteUsername);
            categoryField.setText(existing.category == null ? "" : existing.category);
            tagsField.setText(existing.tags == null ? "" : existing.tags);
            favoriteBox.setSelected(existing.favorite);
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
        grid.add(makeDialogLabel("Category"), 0, 3);
        grid.add(categoryField, 1, 3);
        grid.add(makeDialogLabel("Tags"), 0, 4);
        grid.add(tagsField, 1, 4);
        grid.add(makeDialogLabel("Favourite"), 0, 5);
        grid.add(favoriteBox, 1, 5);
        grid.add(makeDialogLabel("Password"), 0, 6);
        grid.add(passwordField, 1, 6);
        grid.add(makeDialogLabel("Strength"), 0, 7);
        grid.add(new VBox(6, strengthRow, strengthLabel), 1, 7);
        grid.add(makeDialogLabel("Breach Status"), 0, 8);
        grid.add(breachLabel, 1, 8);
        grid.add(makeDialogLabel("Notes"), 0, 9);
        grid.add(notesArea, 1, 9);

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
            String category = categoryField.getText().trim();
            String tags = tagsField.getText().trim();
            String password = passwordField.getText();
            String notes = notesArea.getText().trim();

            if (siteUrl.isBlank() || siteUsername.isBlank()) {
                showAlert(Alert.AlertType.WARNING, "Credential", "Site URL and username are required.");
                return null;
            }
            if (siteName.isBlank()) {
                siteName = siteUrl;
            }
            if (existing == null && !PasswordStrength.meetsCredentialPasswordRequirements(password)) {
                showAlert(Alert.AlertType.WARNING, "Credential", "Password must be at least 4 characters.");
                return null;
            }

            return new CredentialForm(siteUrl, siteName, siteUsername, password, notes, category, tags,
                    favoriteBox.isSelected());
        });

        return dialog.showAndWait();
    }

    private void scheduleBreachCheck(String password, Label breachLabel, PauseTransition delay, int[] requestCounter) {
        int requestId = ++requestCounter[0];
        delay.stop();

        if (password == null || password.isBlank()) {
            setBreachStatus(breachLabel, "", PasswordStrength.COLOR_EMPTY);
            return;
        }

        setBreachStatus(breachLabel, "Checking...", "#8edfff");
        delay.setOnFinished(event -> runBreachCheck(password, breachLabel, requestCounter, requestId));
        delay.playFromStart();
    }

    private void runBreachCheck(String password, Label breachLabel, int[] requestCounter, int requestId) {
        Task<PasswordBreachChecker.BreachResult> task = new Task<>() {
            @Override
            protected PasswordBreachChecker.BreachResult call() throws Exception {
                return breachChecker.check(password);
            }
        };

        task.setOnSucceeded(event -> {
            if (requestCounter[0] != requestId) {
                return;
            }
            PasswordBreachChecker.BreachResult result = task.getValue();
            if (result.status() == PasswordBreachChecker.Status.FOUND) {
                setBreachStatus(breachLabel,
                        "Found in " + String.format("%,d", result.count()) + " breaches ⚠",
                        PasswordStrength.COLOR_WEAK);
            } else if (result.status() == PasswordBreachChecker.Status.NOT_FOUND) {
                setBreachStatus(breachLabel, "Not found ✓", PasswordStrength.COLOR_STRONG);
            } else {
                setBreachStatus(breachLabel, "", PasswordStrength.COLOR_EMPTY);
            }
        });

        task.setOnFailed(event -> {
            if (requestCounter[0] == requestId) {
                setBreachStatus(breachLabel, "Unable to check right now", PasswordStrength.COLOR_FAIR);
            }
        });

        Thread thread = new Thread(task, "hibp-password-check");
        thread.setDaemon(true);
        thread.start();
    }

    private void setBreachStatus(Label breachLabel, String text, String color) {
        breachLabel.setText(text);
        breachLabel.setStyle("-fx-text-fill: " + color + ";");
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
            readString(rs, "category"),
            readString(rs, "tags"),
            readBoolean(rs, "is_favorite"),
            createdAtMs,
            updatedAtMs);
    }

    private void refreshCategoryFilter() {
        if (categoryFilter == null) {
            return;
        }

        String selected = categoryFilter.getValue();
        TreeSet<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Credential credential : credentials) {
            if (credential.category != null && !credential.category.isBlank()) {
                categories.add(credential.category.trim());
            }
        }

        List<String> options = new ArrayList<>();
        options.add("All categories");
        options.add("Favourites");
        options.add("Recent");
        options.addAll(categories);
        categoryFilter.getItems().setAll(options);
        if (selected != null && options.contains(selected)) {
            categoryFilter.getSelectionModel().select(selected);
        } else {
            categoryFilter.getSelectionModel().selectFirst();
        }
        applyFilters();
    }

    private String readString(ResultSet rs, String column) {
        try {
            String value = rs.getString(column);
            return value == null ? "" : value;
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean readBoolean(ResultSet rs, String column) {
        try {
            return rs.getInt(column) != 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private long readTimestamp(ResultSet rs, String column) {
        try {
            long value = rs.getLong(column);
            if (!rs.wasNull() && value >= MIN_VALID_TIMESTAMP_MS) {
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
        if (updatedAtMs < MIN_VALID_TIMESTAMP_MS) {
            return "—";
        }
        return DATE_FMT.format(Instant.ofEpochMilli(updatedAtMs));
    }

    private boolean isRecent(Credential credential) {
        if (credential == null || credential.updated_at < MIN_VALID_TIMESTAMP_MS) {
            return false;
        }
        long thirtyDaysMs = 30L * 24 * 60 * 60 * 1000;
        return System.currentTimeMillis() - credential.updated_at <= thirtyDaysMs;
    }

    private String faviconUrlFor(String siteUrl) {
        String host = hostFor(siteUrl);
        if (host.isBlank()) {
            return null;
        }
        return "https://www.google.com/s2/favicons?sz=32&domain=" + host;
    }

    private String hostFor(String siteUrl) {
        if (siteUrl == null || siteUrl.isBlank()) {
            return "";
        }
        String value = siteUrl.trim();
        try {
            URI uri = URI.create(value.contains("://") ? value : "https://" + value);
            String host = uri.getHost();
            if (host == null) {
                return "";
            }
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private Optional<PasswordChangeForm> showPasswordChangeDialog() {
        Dialog<PasswordChangeForm> dialog = new Dialog<>();
        dialog.setTitle("Change Master Password");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        PasswordField currentField = new PasswordField();
        PasswordField newField = new PasswordField();
        PasswordField confirmField = new PasswordField();
        currentField.getStyleClass().add("dialog-field");
        newField.getStyleClass().add("dialog-field");
        confirmField.getStyleClass().add("dialog-field");

        GridPane grid = new GridPane();
        grid.getStyleClass().add("dialog-grid");
        grid.setPadding(new Insets(12, 16, 8, 16));
        grid.add(makeDialogLabel("Current Master Password"), 0, 0);
        grid.add(currentField, 1, 0);
        grid.add(makeDialogLabel("New Master Password"), 0, 1);
        grid.add(newField, 1, 1);
        grid.add(makeDialogLabel("Confirm New Master Password"), 0, 2);
        grid.add(confirmField, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/css/vault.css").toExternalForm());
        Platform.runLater(currentField::requestFocus);

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            return new PasswordChangeForm(
                    currentField.getText(),
                    newField.getText(),
                    confirmField.getText());
        });

        return dialog.showAndWait();
    }

    private Credential copyCredential(Credential credential) {
        return new Credential(
                credential.id,
                credential.ownerUsername,
                credential.siteUrl,
                credential.siteName,
                credential.siteUsername,
                credential.accountNumber,
                credential.encryptedPassword,
                credential.notes,
                credential.category,
                credential.tags,
                credential.favorite,
                credential.created_at,
                credential.updated_at);
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

    private record CredentialForm(String siteUrl, String siteName, String siteUsername, String password, String notes,
                                  String category, String tags, boolean favorite) {
    }

    private record BiometricSettingForm(boolean enabled, String masterPassword) {
    }

    private record PasswordChangeForm(String currentPassword, String newPassword, String confirmPassword) {
    }
}
