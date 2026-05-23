package com.himalayanvault.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;

/**
 * DatabaseManager — manages SQLite persistence for Himalayan Vault.
 * Handles storage of master password hash/salt and recovery mnemonics.
 */
public class DatabaseManager {

    private static final String DB_NAME = "himalayan-vault.db";
    private static final String DB_PATH = System.getProperty("user.home") + "/.himalayan-vault/";
    private static final String DB_URL_BASE = "jdbc:sqlite:" + DB_PATH + DB_NAME;
    // Connection parameters for reliability and recovery
    private static final String DB_CONNECTION_PARAMS = "?journal_mode=OFF&synchronous=NORMAL&locking_mode=NORMAL";

    private static DatabaseManager instance;
    private Connection connection;

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
                // Connect to database with recovery-friendly parameters
                connection = DriverManager.getConnection(DB_URL_BASE + DB_CONNECTION_PARAMS);
                connection.setAutoCommit(true);
                System.out.println("[DatabaseManager] Connected to SQLite database at: " + DB_URL_BASE);

                // Create tables
                createTables();
                
                // Force database flush to disk
                try (Statement stmt = connection.createStatement()) {
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
            // Table for master password (salt + hash) - multi-user
            String createVaultTable = """
                    CREATE TABLE IF NOT EXISTS vault (
                        username TEXT PRIMARY KEY,
                        salt TEXT NOT NULL,
                        password_hash TEXT NOT NULL,
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
            String createCredentialsTable = """
                    CREATE TABLE IF NOT EXISTS credentials (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        owner_username TEXT NOT NULL,
                        site_url TEXT NOT NULL,
                        site_name TEXT,
                        site_username TEXT NOT NULL,
                        encrypted_password TEXT NOT NULL,
                        notes TEXT,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (owner_username) REFERENCES vault(username) ON DELETE CASCADE
                    )
                    """;

            stmt.execute(createVaultTable);
            stmt.execute(createRecoveryTable);
            stmt.execute(createCredentialsTable);
            System.out.println("[DatabaseManager] Database tables initialized");
        }
    }

    /**
     * Save master password salt and hash to database for a specific user.
     *
     * @param username     the username
     * @param saltBase64   the salt encoded in Base64
     * @param hashBase64   the password hash encoded in Base64
     */
    public void saveMasterPassword(String username, String saltBase64, String hashBase64) {
        String sql = """
                INSERT OR REPLACE INTO vault (username, salt, password_hash, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, saltBase64);
            pstmt.setString(3, hashBase64);
            pstmt.executeUpdate();
            System.out.println("[DatabaseManager] Master password saved for user: " + username);
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Failed to save master password: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Load master password salt from database for a specific user.
     *
     * @param username  the username
     * @return salt as byte array, or null if not found
     */
    public byte[] loadSalt(String username) {
        String sql = "SELECT salt FROM vault WHERE username = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String saltBase64 = rs.getString("salt");
                System.out.println("[DatabaseManager] Salt loaded for user: " + username);
                return Base64.getDecoder().decode(saltBase64);
            } else {
                System.err.println("[DatabaseManager] No salt found for user: " + username);
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Failed to load salt for user " + username + ": " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Load master password hash from database for a specific user.
     *
     * @param username  the username
     * @return password hash as byte array, or null if not found
     */
    public byte[] loadPasswordHash(String username) {
        String sql = "SELECT password_hash FROM vault WHERE username = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String hashBase64 = rs.getString("password_hash");
                System.out.println("[DatabaseManager] Password hash loaded for user: " + username);
                return Base64.getDecoder().decode(hashBase64);
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

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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
                INSERT OR REPLACE INTO recovery (username, mnemonic_words, mnemonic_hash, salt, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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

    /**
     * Load recovery mnemonic words from database for a specific user.
     *
     * @param username  the username
     * @return mnemonic words as String (space-separated), or null if not found
     */
    public String loadMnemonicWords(String username) {
        String sql = "SELECT mnemonic_words FROM recovery WHERE username = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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
        try (Statement stmt = connection.createStatement()) {
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
     *
     * @param username            the owner username
     * @param siteUrl            the URL of the site
     * @param siteName           the display name of the site
     * @param siteUsername       the username for the site
     * @param encryptedPassword  the AES-GCM encrypted password
     * @param notes              optional notes about the credential
     * @return the credential ID, or -1 on error
     */
    public long saveCredential(String username, String siteUrl, String siteName, 
                               String siteUsername, String encryptedPassword, String notes) {
        String sql = """
                INSERT INTO credentials (owner_username, site_url, site_name, site_username, 
                                        encrypted_password, notes, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, username);
            pstmt.setString(2, siteUrl);
            pstmt.setString(3, siteName);
            pstmt.setString(4, siteUsername);
            pstmt.setString(5, encryptedPassword);
            pstmt.setString(6, notes);
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        long id = generatedKeys.getLong(1);
                        System.out.println("[DatabaseManager] Credential saved with ID: " + id + " for user: " + username);
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

    /**
     * Load all credentials for a specific user.
     *
     * @param username the owner username
     * @return ResultSet containing all credentials for the user
     */
    public ResultSet loadCredentialsForUser(String username) throws SQLException {
        String sql = "SELECT * FROM credentials WHERE owner_username = ? ORDER BY updated_at DESC";
        PreparedStatement pstmt = connection.prepareStatement(sql);
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
        String sql = "SELECT * FROM credentials WHERE owner_username = ? AND site_url LIKE ? ORDER BY updated_at DESC";
        PreparedStatement pstmt = connection.prepareStatement(sql);
        pstmt.setString(1, username);
        pstmt.setString(2, "%" + siteUrl + "%");
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
        PreparedStatement pstmt = connection.prepareStatement(sql);
        pstmt.setLong(1, credentialId);
        pstmt.setString(2, username);
        ResultSet rs = pstmt.executeQuery();
        if (rs.next()) {
            return rs;
        }
        return null;
    }

    /**
     * Update a credential.
     *
     * @param credentialId       the credential ID to update
     * @param username           the owner username (for security verification)
     * @param siteUrl           the updated site URL
     * @param siteName          the updated site name
     * @param siteUsername      the updated site username
     * @param encryptedPassword the updated encrypted password
     * @param notes             the updated notes
     * @return true if update was successful, false otherwise
     */
    public boolean updateCredential(long credentialId, String username, String siteUrl, 
                                   String siteName, String siteUsername, 
                                   String encryptedPassword, String notes) {
        String sql = """
                UPDATE credentials SET site_url = ?, site_name = ?, site_username = ?, 
                                      encrypted_password = ?, notes = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND owner_username = ?
                """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, siteUrl);
            pstmt.setString(2, siteName);
            pstmt.setString(3, siteUsername);
            pstmt.setString(4, encryptedPassword);
            pstmt.setString(5, notes);
            pstmt.setLong(6, credentialId);
            pstmt.setString(7, username);
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                System.out.println("[DatabaseManager] Credential " + credentialId + " updated for user: " + username);
                return true;
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Failed to update credential: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Delete a credential.
     *
     * @param credentialId the credential ID to delete
     * @param username     the owner username (for security verification)
     * @return true if deletion was successful, false otherwise
     */
    public boolean deleteCredential(long credentialId, String username) {
        String sql = "DELETE FROM credentials WHERE id = ? AND owner_username = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, credentialId);
            pstmt.setString(2, username);
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                System.out.println("[DatabaseManager] Credential " + credentialId + " deleted for user: " + username);
                return true;
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Failed to delete credential: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

}
