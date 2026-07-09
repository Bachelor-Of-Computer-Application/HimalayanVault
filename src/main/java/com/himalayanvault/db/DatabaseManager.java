package com.himalayanvault.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.List;

import com.himalayanvault.security.Pbkdf2PasswordHasher;

/**
 * DatabaseManager — manages SQLite persistence for Himalayan Vault.
 * Handles storage of master password hash/salt and recovery mnemonics.
 */
public class DatabaseManager {

    private static final String DB_NAME = "himalayan-vault.db";
    private static final String DB_PATH = System.getProperty("user.home") + "/.himalayan-vault/";
    private static final String DB_URL_BASE = "jdbc:sqlite:" + DB_PATH + DB_NAME;
    // WAL keeps crash safety while still allowing efficient reads and writes.
    private static final String DB_CONNECTION_PARAMS = "?journal_mode=WAL&synchronous=NORMAL&locking_mode=NORMAL";

    private static DatabaseManager instance;
    private Connection connection;

    @FunctionalInterface
    public interface SqlTransaction {
        void run(Connection connection) throws SQLException;
    }

    private DatabaseManager() {
        initializeDatabase();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Initialize the database and create necessary tables if they don't exist.
     */
    private void initializeDatabase() {
        File dir = new File(DB_PATH);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File dbFile = new File(DB_PATH + DB_NAME);
        
        // Try to connect, and if we get a corruption error, delete and recreate
        int attempts = 0;
        while (attempts < 2) {
            try {
                openConnection();
                System.out.println("[DatabaseManager] Connected to SQLite database at: " + DB_URL_BASE);

                // Create tables
                createTables();
                
                // Force database flush to disk
                try (Statement stmt = getConnection().createStatement()) {
                    stmt.execute("PRAGMA integrity_check");
                }
                
                // Verify database file was created
                if (dbFile.exists()) {
                    System.out.println("[DatabaseManager] Database file created successfully: " + dbFile.getAbsolutePath() + " (Size: " + dbFile.length() + " bytes)");
                }
                break; // Success, exit loop
                
            } catch (SQLException e) {
                String errorMsg = e.getMessage();
                
                // Check if it's a corruption error
                if (errorMsg != null && (errorMsg.contains("SQLITE_NOTADB") || errorMsg.contains("File opened that is not a database"))) {
                    attempts++;
                    System.out.println("[DatabaseManager] Detected corrupted database (attempt " + attempts + "): " + errorMsg);
                    
                    if (attempts < 2) {
                        // Delete the corrupted files and try again
                        try {
                            if (connection != null) connection.close();
                        } catch (SQLException ignored) {}
                        
                        dbFile.delete();
                        new File(DB_PATH + DB_NAME + "-shm").delete();
                        new File(DB_PATH + DB_NAME + "-wal").delete();
                        System.out.println("[DatabaseManager] Corrupted database files deleted. Retrying...");
                    } else {
                        System.err.println("[DatabaseManager] Failed to initialize database after retry: " + errorMsg);
                        e.printStackTrace();
                        break;
                    }
                } else {
                    System.err.println("[DatabaseManager] Failed to initialize database: " + errorMsg);
                    e.printStackTrace();
                    break;
                }
            }
        }
    }

    /**
     * Create necessary tables if they don't exist.
     */
    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Table for master password - stores Argon2id hash (includes salt)
            // Multi-user support
            String createVaultTable = """
                    CREATE TABLE IF NOT EXISTS vault (
                        username TEXT PRIMARY KEY,
                        password_hash TEXT NOT NULL,
                        biometric_enabled INTEGER DEFAULT 0,
                        salt TEXT,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """;

            // Table for recovery mnemonic (stores words + hash for verification) - multi-user
            String createRecoveryTable = """
                    CREATE TABLE IF NOT EXISTS recovery (
                        username TEXT PRIMARY KEY,
                        mnemonic_words TEXT NOT NULL,
                        mnemonic_hash TEXT NOT NULL,
                        salt TEXT NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """;

            // Table for API credentials (encrypted credentials for sites)
            // Added 'account_number' to support multiple accounts per site
            String createCredentialsTable = """
                    CREATE TABLE IF NOT EXISTS credentials (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        owner_username TEXT NOT NULL,
                        site_url TEXT NOT NULL,
                        site_name TEXT,
                        site_username TEXT NOT NULL,
                        account_number INTEGER DEFAULT 1,
                        encrypted_password TEXT NOT NULL,
                        notes TEXT,
                        category TEXT DEFAULT '',
                        tags TEXT DEFAULT '',
                        is_favorite INTEGER DEFAULT 0,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (owner_username) REFERENCES vault(username) ON DELETE CASCADE,
                        UNIQUE(owner_username, site_url, site_username, account_number)
                    )
                    """;

                    String createVaultUsernameIndex = """
                        CREATE UNIQUE INDEX IF NOT EXISTS idx_vault_username_unique
                        ON vault(username)
                        """;

                    String createRecoveryUsernameIndex = """
                        CREATE UNIQUE INDEX IF NOT EXISTS idx_recovery_username_unique
                        ON recovery(username)
                        """;

            // Add indexes for faster queries
            String createIndexSiteUrl = """
                    CREATE INDEX IF NOT EXISTS idx_credentials_site_url 
                    ON credentials(owner_username, site_url)
                    """;
            
            String createIndexSiteUsername = """
                    CREATE INDEX IF NOT EXISTS idx_credentials_site_username 
                    ON credentials(owner_username, site_username)
                    """;
            
            String createIndexUpdated = """
                    CREATE INDEX IF NOT EXISTS idx_credentials_updated 
                    ON credentials(owner_username, updated_at)
                    """;

            String createIndexCategory = """
                    CREATE INDEX IF NOT EXISTS idx_credentials_category
                    ON credentials(owner_username, category)
                    """;

            String createIndexFavorite = """
                    CREATE INDEX IF NOT EXISTS idx_credentials_favorite
                    ON credentials(owner_username, is_favorite)
                    """;

            String createIndexLastModified = """
                    CREATE INDEX IF NOT EXISTS idx_credentials_last_modified
                    ON credentials(updated_at)
                    """;

            stmt.execute(createVaultTable);
            migrateVaultTable(stmt);
            stmt.execute(createRecoveryTable);
            stmt.execute(createCredentialsTable);
            migrateCredentialsTable(stmt);
            stmt.execute(createVaultUsernameIndex);
            stmt.execute(createRecoveryUsernameIndex);
            stmt.execute(createIndexSiteUrl);
            stmt.execute(createIndexSiteUsername);
            stmt.execute(createIndexUpdated);
            stmt.execute(createIndexCategory);
            stmt.execute(createIndexFavorite);
            stmt.execute(createIndexLastModified);
            System.out.println("[DatabaseManager] Database tables and indexes initialized");
        }
    }

    private void migrateVaultTable(Statement stmt) throws SQLException {
        if (!hasColumn("vault", "salt")) {
            stmt.execute("ALTER TABLE vault ADD COLUMN salt TEXT");
            System.out.println("[DatabaseManager] Added salt column to vault table");
        }
        if (!hasColumn("vault", "biometric_enabled")) {
            stmt.execute("ALTER TABLE vault ADD COLUMN biometric_enabled INTEGER DEFAULT 0");
            stmt.execute("UPDATE vault SET biometric_enabled = 0 WHERE biometric_enabled IS NULL");
            System.out.println("[DatabaseManager] Added biometric_enabled column to vault table");
        }
    }

    private void migrateCredentialsTable(Statement stmt) throws SQLException {
        if (!hasColumn("credentials", "account_number")) {
            stmt.execute("ALTER TABLE credentials ADD COLUMN account_number INTEGER DEFAULT 1");
            stmt.execute("UPDATE credentials SET account_number = 1 WHERE account_number IS NULL");
            System.out.println("[DatabaseManager] Added account_number column to credentials table");
        }
        if (!hasColumn("credentials", "category")) {
            stmt.execute("ALTER TABLE credentials ADD COLUMN category TEXT DEFAULT ''");
            stmt.execute("UPDATE credentials SET category = '' WHERE category IS NULL");
            System.out.println("[DatabaseManager] Added category column to credentials table");
        }
        if (!hasColumn("credentials", "tags")) {
            stmt.execute("ALTER TABLE credentials ADD COLUMN tags TEXT DEFAULT ''");
            stmt.execute("UPDATE credentials SET tags = '' WHERE tags IS NULL");
            System.out.println("[DatabaseManager] Added tags column to credentials table");
        }
        if (!hasColumn("credentials", "is_favorite")) {
            stmt.execute("ALTER TABLE credentials ADD COLUMN is_favorite INTEGER DEFAULT 0");
            stmt.execute("UPDATE credentials SET is_favorite = 0 WHERE is_favorite IS NULL");
            System.out.println("[DatabaseManager] Added is_favorite column to credentials table");
        }
    }

    private boolean hasColumn(String table, String column) throws SQLException {
        try (ResultSet rs = getConnection().getMetaData().getColumns(null, null, table, column)) {
            return rs.next();
        }
    }

    private synchronized void openConnection() throws SQLException {
        connection = DriverManager.getConnection(DB_URL_BASE + DB_CONNECTION_PARAMS);
        connection.setAutoCommit(true);
        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=NORMAL");
            stmt.execute("PRAGMA foreign_keys=ON");
            stmt.execute("PRAGMA busy_timeout=5000");
        }
    }

    private synchronized Connection ensureConnection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(2)) {
            System.out.println("[DatabaseManager] Reopening stale SQLite connection");
            openConnection();
        }
        return connection;
    }

    /**
     * Save master password hash to database for a specific user.
     * PBKDF2 hashes embed the salt; a duplicate salt column is kept for legacy schemas.
     */
    public void saveMasterPassword(String username, String hashString) throws SQLException {
        String salt = Pbkdf2PasswordHasher.extractEmbeddedSalt(hashString);
        String sql = """
                INSERT INTO vault (username, password_hash, salt, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(username) DO UPDATE SET
                    password_hash = excluded.password_hash,
                    salt = excluded.salt,
                    updated_at = CURRENT_TIMESTAMP
                """;

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, hashString);
            pstmt.setString(3, salt.isEmpty() ? hashString : salt);
            pstmt.executeUpdate();
            System.out.println("[DatabaseManager] Master password saved for user: " + username);
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Failed to save master password: " + e.getMessage());
            throw e;
        }
    }

    public void saveMasterPassword(Connection conn, String username, String hashString) throws SQLException {
        String salt = Pbkdf2PasswordHasher.extractEmbeddedSalt(hashString);
        String sql = """
                INSERT INTO vault (username, password_hash, salt, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, hashString);
            pstmt.setString(3, salt.isEmpty() ? hashString : salt);
            pstmt.executeUpdate();
        }
    }

    /**
     * Load master password hash from database for a specific user.
     * Returns the Argon2id hash string (which includes salt).
     *
     * @param username  the username
     * @return Argon2id hash string, or null if not found
     */
    public String loadPasswordHash(String username) {
        String sql = "SELECT password_hash FROM vault WHERE username = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String hashString = rs.getString("password_hash");
                System.out.println("[DatabaseManager] Password hash loaded for user: " + username);
                return hashString;
            } else {
                System.err.println("[DatabaseManager] No password hash found for user: " + username);
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Failed to load password hash for user " + username + ": " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Check if master password is already set for a specific user.
     *
     * @param username  the username
     * @return true if vault credentials exist for this user, false otherwise
     */
    public boolean isVaultInitialized(String username) {
        String sql = "SELECT COUNT(*) as count FROM vault WHERE username = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count") > 0;
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Failed to check vault initialization for user " + username + ": " + e.getMessage());
        }
        return false;
    }

    public boolean isBiometricEnabled(String username) {
        String sql = "SELECT biometric_enabled FROM vault WHERE username = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt("biometric_enabled") == 1;
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Failed to load biometric setting for user " + username + ": " + e.getMessage());
            return false;
        }
    }

    public boolean setBiometricEnabled(String username, boolean enabled) {
        String sql = """
                UPDATE vault
                SET biometric_enabled = ?, updated_at = CURRENT_TIMESTAMP
                WHERE username = ?
                """;

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, enabled ? 1 : 0);
            pstmt.setString(2, username);
            boolean updated = pstmt.executeUpdate() == 1;
            if (updated) {
                System.out.println("[DatabaseManager] Biometric unlock "
                        + (enabled ? "enabled" : "disabled") + " for user: " + username);
            }
            return updated;
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Failed to update biometric setting for user " + username + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Save recovery mnemonic words and their hash to database for a specific user.
     *
     * @param username                  the username
     * @param mnemonicWordsBase64       the mnemonic words (space-separated) encoded in Base64
     * @param mnemonicHashBase64        the mnemonic hash encoded in Base64
     * @param saltBase64                the salt used for hashing, encoded in Base64
     */
    public void saveMnemonicHash(String username, String mnemonicWordsBase64, String mnemonicHashBase64, String saltBase64) {
        String sql = """
                INSERT INTO recovery (username, mnemonic_words, mnemonic_hash, salt, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(username) DO UPDATE SET
                    mnemonic_words = excluded.mnemonic_words,
                    mnemonic_hash = excluded.mnemonic_hash,
                    salt = excluded.salt,
                    updated_at = CURRENT_TIMESTAMP
                """;

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, mnemonicWordsBase64);
            pstmt.setString(3, mnemonicHashBase64);
            pstmt.setString(4, saltBase64);
            pstmt.executeUpdate();
            System.out.println("[DatabaseManager] Mnemonic words and hash saved for user: " + username);
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Failed to save mnemonic hash: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveMnemonicHash(Connection conn, String username, String mnemonicWordsBase64, String mnemonicHashBase64, String saltBase64) throws SQLException {
        String sql = """
                INSERT INTO recovery (username, mnemonic_words, mnemonic_hash, salt, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, mnemonicWordsBase64);
            pstmt.setString(3, mnemonicHashBase64);
            pstmt.setString(4, saltBase64);
            pstmt.executeUpdate();
        }
    }

    public void withTransaction(SqlTransaction transaction) throws SQLException {
        Connection conn = getConnection();
        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            transaction.run(conn);
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } catch (RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(previousAutoCommit);
        }
    }

    /**
     * Load recovery mnemonic words from database for a specific user.
     *
     * @param username  the username
     * @return mnemonic words as String (space-separated), or null if not found
     */
    public String loadMnemonicWords(String username) {
        String sql = "SELECT mnemonic_words FROM recovery WHERE username = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String wordsBase64 = rs.getString("mnemonic_words");
                return new String(Base64.getDecoder().decode(wordsBase64));
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Failed to load mnemonic words for user " + username + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Load recovery mnemonic hash from database for a specific user.
     *
     * @param username  the username
     * @return mnemonic hash as byte array, or null if not found
     */
    public byte[] loadMnemonicHash(String username) {
        String sql = "SELECT mnemonic_hash FROM recovery WHERE username = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String hashBase64 = rs.getString("mnemonic_hash");
                System.out.println("[DatabaseManager] Mnemonic hash loaded for user: " + username);
                return Base64.getDecoder().decode(hashBase64);
            } else {
                System.err.println("[DatabaseManager] No mnemonic hash found for user: " + username);
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Failed to load mnemonic hash for user " + username + ": " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Load recovery mnemonic salt from database for a specific user.
     *
     * @param username  the username
     * @return mnemonic salt as byte array, or null if not found
     */
    public byte[] loadMnemonicSalt(String username) {
        String sql = "SELECT salt FROM recovery WHERE username = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String saltBase64 = rs.getString("salt");
                System.out.println("[DatabaseManager] Mnemonic salt loaded for user: " + username);
                return Base64.getDecoder().decode(saltBase64);
            } else {
                System.err.println("[DatabaseManager] No mnemonic salt found for user: " + username);
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Failed to load mnemonic salt for user " + username + ": " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Check if recovery mnemonic is already set for a specific user.
     *
     * @param username  the username
     * @return true if recovery mnemonic exists for this user, false otherwise
     */
    public boolean isRecoverySet(String username) {
        String sql = "SELECT COUNT(*) as count FROM recovery WHERE username = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count") > 0;
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Failed to check recovery status for user " + username + ": " + e.getMessage());
        }
        return false;
    }

    /**
     * Get a live database connection for package-local SQL helpers.
     *
     * @return the JDBC connection
     */
    synchronized java.sql.Connection getConnection() throws SQLException {
        return ensureConnection();
    }

    /**
     * Close the database connection.
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                System.out.println("[DatabaseManager] Database connection closed");
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Failed to close database: " + e.getMessage());
            }
        }
    }

    /**
     * Clear all vault data (reset vault).
     */
    public void clearVault() {
        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute("DELETE FROM vault WHERE id = 1");
            stmt.execute("DELETE FROM recovery WHERE id = 1");
            System.out.println("[DatabaseManager] Vault cleared");
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Failed to clear vault: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Credentials management (API)
    // ────────────────────────────────────────────────────────────────

    /**
     * Save a credential (site username + encrypted password) for a user.
     * Supports multiple accounts per site using account_number field.
     *
     * @param username            the owner username
     * @param siteUrl            the URL of the site
     * @param siteName           the display name of the site
     * @param siteUsername       the username for the site
     * @param accountNumber      account number (default 1, increments for multiple accounts per site)
     * @param encryptedPassword  the AES-GCM encrypted password
     * @param notes              optional notes about the credential
     * @return the credential ID, or -1 on error
     */
    public long saveCredential(String username, String siteUrl, String siteName, 
                               String siteUsername, int accountNumber, String encryptedPassword, String notes) {
        return saveCredential(username, siteUrl, siteName, siteUsername, accountNumber, encryptedPassword, notes,
                "", "", false);
    }

    public long saveCredential(String username, String siteUrl, String siteName,
                               String siteUsername, int accountNumber, String encryptedPassword, String notes,
                               String category, String tags, boolean favorite) {
        String sql = """
                INSERT INTO credentials (owner_username, site_url, site_name, site_username, 
                                        account_number, encrypted_password, notes, category, tags, is_favorite, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """;

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, username);
            pstmt.setString(2, siteUrl);
            pstmt.setString(3, siteName);
            pstmt.setString(4, siteUsername);
            pstmt.setInt(5, accountNumber);
            pstmt.setString(6, encryptedPassword);
            pstmt.setString(7, notes);
            pstmt.setString(8, category != null ? category : "");
            pstmt.setString(9, tags != null ? tags : "");
            pstmt.setInt(10, favorite ? 1 : 0);
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        long id = generatedKeys.getLong(1);
                        System.out.println("[DatabaseManager] Credential saved with ID: " + id + " (account " + accountNumber + ") for user: " + username);
                        return id;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Failed to save credential: " + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }

    public boolean credentialExists(String username, String siteUrl, String siteUsername) {
        String sql = """
                SELECT COUNT(*) AS count FROM credentials
                WHERE owner_username = ? AND lower(site_url) = lower(?) AND lower(site_username) = lower(?)
                """;

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, siteUrl);
            pstmt.setString(3, siteUsername);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt("count") > 0;
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Duplicate check failed: " + e.getMessage());
            return false;
        }
    }

    public Long findCredentialId(String username, String siteUrl, String siteUsername) {
        String sql = """
                SELECT id FROM credentials
                WHERE owner_username = ? AND lower(site_url) = lower(?) AND lower(site_username) = lower(?)
                ORDER BY account_number ASC
                LIMIT 1
                """;

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, siteUrl);
            pstmt.setString(3, siteUsername);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() ? rs.getLong("id") : null;
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Credential lookup failed: " + e.getMessage());
            return null;
        }
    }

    public boolean upsertCredentialByUrlAndUsername(String ownerUsername, com.himalayanvault.models.Credential credential) {
        Long existingId = findCredentialId(ownerUsername, credential.siteUrl, credential.siteUsername);
        if (existingId == null) {
            return saveCredential(
                    ownerUsername,
                    credential.siteUrl,
                    credential.siteName != null ? credential.siteName : credential.siteUrl,
                    credential.siteUsername,
                    credential.accountNumber > 0 ? credential.accountNumber : 1,
                    credential.encryptedPassword,
                    credential.notes != null ? credential.notes : "",
                    credential.category != null ? credential.category : "",
                    credential.tags != null ? credential.tags : "",
                    credential.favorite) > 0;
        }

        return updateCredential(
                existingId,
                ownerUsername,
                credential.siteUrl,
                credential.siteName != null ? credential.siteName : credential.siteUrl,
                credential.siteUsername,
                credential.encryptedPassword,
                credential.notes != null ? credential.notes : "",
                credential.category != null ? credential.category : "",
                credential.tags != null ? credential.tags : "",
                credential.favorite);
    }

    /**
     * Update an existing credential.
     *
     * @param credentialId the credential ID to update
     * @param username the owner username (for security verification)
     * @param siteUrl the updated site URL
     * @param siteName the updated site name
     * @param siteUsername the updated username
     * @param encryptedPassword the updated encrypted password
     * @param notes the updated notes
     * @return true if update was successful
     */
    public boolean updateCredential(long credentialId, String username, String siteUrl, 
                                   String siteName, String siteUsername, String encryptedPassword, String notes) {
        return updateCredential(credentialId, username, siteUrl, siteName, siteUsername, encryptedPassword, notes,
                "", "", false);
    }

    public boolean updateCredential(long credentialId, String username, String siteUrl,
                                   String siteName, String siteUsername, String encryptedPassword, String notes,
                                   String category, String tags, boolean favorite) {
        String sql = """
                UPDATE credentials 
                SET site_url = ?, site_name = ?, site_username = ?, 
                    encrypted_password = ?, notes = ?, category = ?, tags = ?, is_favorite = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND owner_username = ?
                """;

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, siteUrl);
            pstmt.setString(2, siteName);
            pstmt.setString(3, siteUsername);
            pstmt.setString(4, encryptedPassword);
            pstmt.setString(5, notes);
            pstmt.setString(6, category != null ? category : "");
            pstmt.setString(7, tags != null ? tags : "");
            pstmt.setInt(8, favorite ? 1 : 0);
            pstmt.setLong(9, credentialId);
            pstmt.setString(10, username);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                System.out.println("[DatabaseManager] Credential " + credentialId + " updated for user: " + username);
                return true;
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Failed to update credential: " + e.getMessage());
        }
        return false;
    }

    public void rotateMasterPasswordAndCredentials(String username, String newPasswordHash,
                                                   List<com.himalayanvault.models.Credential> reencryptedCredentials)
            throws SQLException {
        Connection conn = getConnection();
        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            String updateCredentialSql = """
                    UPDATE credentials
                    SET encrypted_password = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND owner_username = ?
                    """;
            try (PreparedStatement pstmt = conn.prepareStatement(updateCredentialSql)) {
                for (com.himalayanvault.models.Credential credential : reencryptedCredentials) {
                    pstmt.setString(1, credential.encryptedPassword);
                    pstmt.setLong(2, credential.id);
                    pstmt.setString(3, username);
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }

            String salt = Pbkdf2PasswordHasher.extractEmbeddedSalt(newPasswordHash);
            String updateVaultSql = """
                    UPDATE vault
                    SET password_hash = ?, salt = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE username = ?
                    """;
            try (PreparedStatement pstmt = conn.prepareStatement(updateVaultSql)) {
                pstmt.setString(1, newPasswordHash);
                pstmt.setString(2, salt.isEmpty() ? newPasswordHash : salt);
                pstmt.setString(3, username);
                if (pstmt.executeUpdate() != 1) {
                    throw new SQLException("No vault account found for " + username);
                }
            }

            conn.commit();
            System.out.println("[DatabaseManager] Rotated master password and re-encrypted "
                    + reencryptedCredentials.size() + " credential(s) for user: " + username);
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(previousAutoCommit);
        }
    }

    /**
     * Delete a credential.
     *
     * @param credentialId the credential ID to delete
     * @param username the owner username (for security verification)
     * @return true if deletion was successful
     */
    public boolean deleteCredential(long credentialId, String username) {
        String sql = "DELETE FROM credentials WHERE id = ? AND owner_username = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setLong(1, credentialId);
            pstmt.setString(2, username);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                System.out.println("[DatabaseManager] Credential " + credentialId + " deleted for user: " + username);
                return true;
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Failed to delete credential: " + e.getMessage());
        }
        return false;
    }

    /**
     * Load all credentials for a specific user.
     *
     * @param username the owner username
     * @return ResultSet containing all credentials for the user
     */
    public ResultSet loadCredentialsForUser(String username) throws SQLException {
        String sql = """
                SELECT * FROM credentials
                WHERE owner_username = ?
                ORDER BY is_favorite DESC, lower(category) ASC, updated_at DESC
                """;
        PreparedStatement pstmt = getConnection().prepareStatement(sql);
        pstmt.setString(1, username);
        return pstmt.executeQuery();
    }

    /**
     * Load credentials matching a site URL for a specific user.
     *
     * @param username the owner username
     * @param siteUrl  the site URL to match (substring match)
     * @return ResultSet containing matching credentials
     */
    public ResultSet loadCredentialsBySite(String username, String siteUrl) throws SQLException {
        String sql = """
                SELECT * FROM credentials
                WHERE owner_username = ?
                  AND (site_url LIKE ? OR site_username LIKE ? OR notes LIKE ? OR category LIKE ? OR tags LIKE ?)
                ORDER BY is_favorite DESC, lower(category) ASC, updated_at DESC
                """;
        PreparedStatement pstmt = getConnection().prepareStatement(sql);
        pstmt.setString(1, username);
        String pattern = "%" + siteUrl + "%";
        pstmt.setString(2, pattern);
        pstmt.setString(3, pattern);
        pstmt.setString(4, pattern);
        pstmt.setString(5, pattern);
        pstmt.setString(6, pattern);
        return pstmt.executeQuery();
    }

    /**
     * Load a specific credential by ID.
     *
     * @param credentialId the credential ID
     * @param username     the owner username (for security verification)
     * @return the credential details, or null if not found
     */
    public ResultSet loadCredentialById(long credentialId, String username) throws SQLException {
        String sql = "SELECT * FROM credentials WHERE id = ? AND owner_username = ?";
        PreparedStatement pstmt = getConnection().prepareStatement(sql);
        pstmt.setLong(1, credentialId);
        pstmt.setString(2, username);
        ResultSet rs = pstmt.executeQuery();
        if (rs.next()) {
            return rs;
        }
        return null;
    }


}
