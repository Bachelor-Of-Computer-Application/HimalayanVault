package com.himalayanvault.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.himalayanvault.models.Credential;

/**
 * CredentialSearcher — search and filter credentials with multiple criteria.
 * 
 * Supported filters:
 * - By site name/URL (partial match)
 * - By username (partial match)
 * - By notes (full-text search)
 * - By creation/modification date range
 * - Pagination support
 */
public class CredentialSearcher {

    private final DatabaseManager db;

    public CredentialSearcher(DatabaseManager database) {
        this.db = database;
    }

    /**
     * Search credentials by site URL (partial match).
     *
     * @param username the vault owner
     * @param siteUrl partial or full site URL
     * @return matching credentials
     */
    public List<Credential> searchBySiteUrl(String username, String siteUrl) {
        String sql = """
                SELECT * FROM credentials 
                WHERE owner_username = ? AND site_url LIKE ? 
                ORDER BY updated_at DESC
                """;

        return executeSearch(sql, username, "%" + siteUrl + "%");
    }

    /**
     * Search credentials by username (partial match).
     *
     * @param vaultOwner the vault owner
     * @param siteUsername partial username or email
     * @return matching credentials
     */
    public List<Credential> searchByUsername(String vaultOwner, String siteUsername) {
        String sql = """
                SELECT * FROM credentials 
                WHERE owner_username = ? AND site_username LIKE ? 
                ORDER BY updated_at DESC
                """;

        return executeSearch(sql, vaultOwner, "%" + siteUsername + "%");
    }

    /**
     * Search in notes using full-text search.
     *
     * @param vaultOwner the vault owner
     * @param searchTerm search text
     * @return matching credentials
     */
    public List<Credential> searchNotes(String vaultOwner, String searchTerm) {
        String sql = """
                SELECT * FROM credentials 
                WHERE owner_username = ? AND notes LIKE ? 
                ORDER BY updated_at DESC
                """;

        return executeSearch(sql, vaultOwner, "%" + searchTerm + "%");
    }

    /**
     * Combined search across all text fields.
     *
     * @param vaultOwner the vault owner
     * @param searchTerm search text
     * @return matching credentials
     */
    public List<Credential> searchAll(String vaultOwner, String searchTerm) {
        String pattern = "%" + searchTerm + "%";
        String sql = """
                SELECT * FROM credentials 
                WHERE owner_username = ? AND 
                (site_url LIKE ? OR site_username LIKE ? OR site_name LIKE ? OR notes LIKE ?)
                ORDER BY updated_at DESC
                """;

        try (PreparedStatement pstmt = db.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, vaultOwner);
            pstmt.setString(2, pattern);
            pstmt.setString(3, pattern);
            pstmt.setString(4, pattern);
            pstmt.setString(5, pattern);

            return resultSetToCredentials(pstmt.executeQuery());
        } catch (SQLException e) {
            System.err.println("[CredentialSearcher] Search failed: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get credentials with pagination.
     *
     * @param vaultOwner the vault owner
     * @param offset number of records to skip
     * @param limit number of records to return
     * @return paginated credentials
     */
    public List<Credential> getCredentialsPaginated(String vaultOwner, int offset, int limit) {
        String sql = """
                SELECT * FROM credentials 
                WHERE owner_username = ? 
                ORDER BY updated_at DESC 
                LIMIT ? OFFSET ?
                """;

        try (PreparedStatement pstmt = db.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, vaultOwner);
            pstmt.setInt(2, limit);
            pstmt.setInt(3, offset);

            return resultSetToCredentials(pstmt.executeQuery());
        } catch (SQLException e) {
            System.err.println("[CredentialSearcher] Pagination query failed: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get total credential count.
     *
     * @param vaultOwner the vault owner
     * @return total credentials
     */
    public int getTotalCount(String vaultOwner) {
        String sql = "SELECT COUNT(*) as count FROM credentials WHERE owner_username = ?";

        try (PreparedStatement pstmt = db.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, vaultOwner);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            System.err.println("[CredentialSearcher] Count query failed: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Find duplicate credentials (same site URL + username).
     *
     * @param vaultOwner the vault owner
     * @return list of duplicate credentials
     */
    public List<CredentialDuplicate> findDuplicates(String vaultOwner) {
        String sql = """
                SELECT site_url, site_username, COUNT(*) as count 
                FROM credentials 
                WHERE owner_username = ? 
                GROUP BY site_url, site_username 
                HAVING count > 1
                """;

        List<CredentialDuplicate> duplicates = new ArrayList<>();
        try (PreparedStatement pstmt = db.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, vaultOwner);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                duplicates.add(new CredentialDuplicate(
                    rs.getString("site_url"),
                    rs.getString("site_username"),
                    rs.getInt("count")
                ));
            }
        } catch (SQLException e) {
            System.err.println("[CredentialSearcher] Duplicate detection failed: " + e.getMessage());
        }
        return duplicates;
    }

    /**
     * Get recently modified credentials.
     *
     * @param vaultOwner the vault owner
     * @param limit number of records
     * @return recently modified credentials
     */
    public List<Credential> getRecentlyModified(String vaultOwner, int limit) {
        String sql = """
                SELECT * FROM credentials 
                WHERE owner_username = ? 
                ORDER BY updated_at DESC 
                LIMIT ?
                """;

        try (PreparedStatement pstmt = db.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, vaultOwner);
            pstmt.setInt(2, limit);

            return resultSetToCredentials(pstmt.executeQuery());
        } catch (SQLException e) {
            System.err.println("[CredentialSearcher] Recent query failed: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get credentials created within date range.
     *
     * @param vaultOwner the vault owner
     * @param startTime start timestamp (milliseconds)
     * @param endTime end timestamp (milliseconds)
     * @return credentials in date range
     */
    public List<Credential> getByDateRange(String vaultOwner, long startTime, long endTime) {
        String sql = """
                SELECT * FROM credentials 
                WHERE owner_username = ? 
                AND created_at >= datetime(?, 'unixepoch') 
                AND created_at <= datetime(?, 'unixepoch')
                ORDER BY updated_at DESC
                """;

        try (PreparedStatement pstmt = db.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, vaultOwner);
            pstmt.setLong(2, startTime / 1000);  // Convert to seconds for SQLite
            pstmt.setLong(3, endTime / 1000);

            return resultSetToCredentials(pstmt.executeQuery());
        } catch (SQLException e) {
            System.err.println("[CredentialSearcher] Date range query failed: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get all credentials for a user (for export/backup).
     *
     * @param vaultOwner the vault owner
     * @return all credentials
     */
    public List<Credential> getAllCredentials(String vaultOwner) {
        String sql = "SELECT * FROM credentials WHERE owner_username = ? ORDER BY site_url, site_username";

        try (PreparedStatement pstmt = db.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, vaultOwner);
            return resultSetToCredentials(pstmt.executeQuery());
        } catch (SQLException e) {
            System.err.println("[CredentialSearcher] Get all failed: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────

    private List<Credential> executeSearch(String sql, String username, String... params) {
        try (PreparedStatement pstmt = db.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            for (int i = 0; i < params.length; i++) {
                pstmt.setString(i + 2, params[i]);
            }
            return resultSetToCredentials(pstmt.executeQuery());
        } catch (SQLException e) {
            System.err.println("[CredentialSearcher] Search failed: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<Credential> resultSetToCredentials(ResultSet rs) throws SQLException {
        List<Credential> credentials = new ArrayList<>();
        while (rs.next()) {
            Credential cred = new Credential();
            cred.id = rs.getInt("id");
            cred.ownerUsername = rs.getString("owner_username");
            cred.siteUrl = rs.getString("site_url");
            cred.siteName = rs.getString("site_name");
            cred.siteUsername = rs.getString("site_username");
            cred.accountNumber = rs.getInt("account_number");
            cred.encryptedPassword = rs.getString("encrypted_password");
            cred.notes = rs.getString("notes");
            cred.category = readString(rs, "category");
            cred.tags = readString(rs, "tags");
            cred.favorite = readBoolean(rs, "is_favorite");
            cred.created_at = rs.getLong("created_at");
            cred.updated_at = rs.getLong("updated_at");
            credentials.add(cred);
        }
        return credentials;
    }

    private String readString(ResultSet rs, String column) {
        try {
            String value = rs.getString(column);
            return value == null ? "" : value;
        } catch (SQLException ignored) {
            return "";
        }
    }

    private boolean readBoolean(ResultSet rs, String column) {
        try {
            return rs.getInt(column) != 0;
        } catch (SQLException ignored) {
            return false;
        }
    }

    /**
     * Represents a duplicate credential entry.
     */
    public static class CredentialDuplicate {
        public String siteUrl;
        public String username;
        public int count;

        public CredentialDuplicate(String siteUrl, String username, int count) {
            this.siteUrl = siteUrl;
            this.username = username;
            this.count = count;
        }
    }
}
