package com.himalayanvault.db;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Database Migration Tests")
class DatabaseMigrationTest {

    @Test
    @DisplayName("Initialized credentials table contains migrated fields")
    void credentialsTableContainsMigratedFields() throws Exception {
        DatabaseManager db = DatabaseManager.getInstance();
        Set<String> columns = columns(db, "credentials");

        assertTrue(columns.contains("account_number"));
        assertTrue(columns.contains("category"));
        assertTrue(columns.contains("tags"));
        assertTrue(columns.contains("is_favorite"));
    }

    @Test
    @DisplayName("Initialized vault table contains legacy-compatible salt field")
    void vaultTableContainsMigratedSaltField() throws Exception {
        DatabaseManager db = DatabaseManager.getInstance();
        Set<String> columns = columns(db, "vault");

        assertTrue(columns.contains("salt"));
    }

    private static Set<String> columns(DatabaseManager db, String table) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (ResultSet rs = db.getConnection().getMetaData().getColumns(null, null, table, null)) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"));
            }
        }
        return columns;
    }
}
