package com.himalayanvault.export;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.himalayanvault.models.Credential;

/**
 * Imports and exports plaintext CSV for migration to or from other password
 * managers. Encrypted .hvlt remains the primary backup format.
 *
 * Export: choose a CsvFormat to produce a file the target manager recognises.
 * Import: universal — aliases cover Bitwarden, LastPass, 1Password, Chrome,
 *         Firefox, Dashlane, and generic CSV.
 *
 * IMPORTANT: passwords coming out of importCsv() are PLAINTEXT.
 * The caller (VaultController) must encrypt them with encryptForStorage()
 * before persisting to the database.
 */
public class CsvCredentialTransfer {

    // -------------------------------------------------------------------------
    // Export format enum
    // -------------------------------------------------------------------------

    public enum CsvFormat {
        /** Generic HimalayanVault CSV — readable by most importers. */
        GENERIC,
        /** Bitwarden-compatible CSV (folder, favorite, type, reprompt columns). */
        BITWARDEN,
        /** LastPass-compatible CSV (url, username, password, extra, name, grouping, fav). */
        LASTPASS
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Export credentials to CSV in the specified format.
     *
     * @param credentials      list of credentials to export
     * @param passwordResolver decrypts encryptedPassword to plaintext for the CSV
     * @param format           target password manager format
     * @return CSV string with \r\n line endings (Windows-compatible)
     */
    public String exportCsv(
            List<Credential> credentials,
            PasswordResolver passwordResolver,
            CsvFormat format) {

        return switch (format) {
            case GENERIC   -> exportGeneric(credentials, passwordResolver);
            case BITWARDEN -> exportBitwarden(credentials, passwordResolver);
            case LASTPASS  -> exportLastPass(credentials, passwordResolver);
        };
    }

    /**
     * Import credentials from a CSV string.
     * Handles Bitwarden, LastPass, 1Password, Chrome, Firefox, Dashlane, and
     * generic CSV via header-alias matching.
     *
     * NOTE: the encryptedPassword field on each returned Credential contains
     * PLAINTEXT. Encrypt it before saving to the database.
     */
    public List<Credential> importCsv(String csvText) {
        List<List<String>> rows = parse(csvText);
        if (rows.isEmpty()) {
            return List.of();
        }

        List<String> headers = rows.get(0).stream()
                .map(CsvCredentialTransfer::normalizeHeader)
                .toList();

        List<Credential> credentials = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            Map<String, String> row = rowMap(headers, rows.get(i));
            Credential credential = toCredential(row);

            // Skip entirely blank rows
            if (credential.siteUrl.isBlank()
                    && credential.siteName.isBlank()
                    && credential.siteUsername.isBlank()
                    && credential.encryptedPassword.isBlank()) {
                continue;
            }

            // Ensure name always has a value
            if (credential.siteName.isBlank()) {
                credential.siteName = credential.siteUrl.isBlank()
                        ? "Imported" : credential.siteUrl;
            }

            credentials.add(credential);
        }
        return credentials;
    }

    // -------------------------------------------------------------------------
    // Export implementations
    // -------------------------------------------------------------------------

    private String exportGeneric(
            List<Credential> credentials,
            PasswordResolver passwordResolver) {

        StringBuilder csv = new StringBuilder();
        appendRow(csv, List.of("name", "url", "username", "password", "notes",
                "category", "tags", "favorite"));

        for (Credential c : credentials) {
            appendRow(csv, List.of(
                    value(c.siteName, c.siteUrl),
                    value(c.siteUrl),
                    value(c.siteUsername),
                    value(passwordResolver.resolvePassword(c)),
                    value(c.notes),
                    value(c.category),
                    value(c.tags),
                    c.favorite ? "1" : "0"));
        }
        return csv.toString();
    }

    private String exportBitwarden(
            List<Credential> credentials,
            PasswordResolver passwordResolver) {

        StringBuilder csv = new StringBuilder();
        appendRow(csv, List.of(
                "folder", "favorite", "type", "name", "notes",
                "fields", "reprompt", "login_uri", "login_username",
                "login_password", "login_totp"));

        for (Credential c : credentials) {
            appendRow(csv, List.of(
                    value(c.category),
                    c.favorite ? "1" : "0",
                    "login",
                    value(c.siteName, c.siteUrl),
                    value(c.notes),
                    "",          // custom fields — not supported
                    "0",         // reprompt off
                    value(c.siteUrl),
                    value(c.siteUsername),
                    value(passwordResolver.resolvePassword(c)),
                    ""));        // TOTP — not supported
        }
        return csv.toString();
    }

    private String exportLastPass(
            List<Credential> credentials,
            PasswordResolver passwordResolver) {

        StringBuilder csv = new StringBuilder();
        appendRow(csv, List.of(
                "url", "username", "password", "extra",
                "name", "grouping", "fav"));

        for (Credential c : credentials) {
            appendRow(csv, List.of(
                    value(c.siteUrl),
                    value(c.siteUsername),
                    value(passwordResolver.resolvePassword(c)),
                    value(c.notes),
                    value(c.siteName, c.siteUrl),
                    value(c.category),
                    c.favorite ? "1" : "0"));
        }
        return csv.toString();
    }

    // -------------------------------------------------------------------------
    // Import: credential mapping with expanded aliases
    // -------------------------------------------------------------------------

    private Credential toCredential(Map<String, String> row) {
        Credential c = new Credential();

        c.siteName = first(row,
                "name", "title", "site", "site name", "item name",
                "display name", "account");

        c.siteUrl = first(row,
                "login uri", "loginuri", "login_uri",
                "login url", "loginurl", "login_url",
                "url", "uri", "website", "web site",
                "http password",    // Firefox
                "origin url");      // Chrome

        c.siteUsername = first(row,
                "login username", "loginusername", "login_username",
                "username", "user name",
                "email",
                "login",
                "user");            // Dashlane

        // NOTE: this field contains PLAINTEXT from the CSV.
        // The caller must call encryptForStorage() before saving to DB.
        c.encryptedPassword = first(row,
                "login password", "loginpassword", "login_password",
                "password", "pass",
                "http password",    // Firefox uses same column for user + pass
                "password 1");      // some Dashlane exports

        c.notes = first(row,
                "notes", "note", "extra", "comments", "comment",
                "secure note");

        c.category = first(row,
                "category", "folder", "grouping", "group",
                "type", "collection");

        c.tags = first(row, "tags", "tag", "labels");

        c.favorite = parseFavorite(first(row,
                "favorite", "fav", "starred", "is favorite"));

        c.accountNumber = 1;

        // Fallback: use URL as name if name is still blank
        if (c.siteName.isBlank()) {
            c.siteName = c.siteUrl;
        }

        return c;
    }

    // -------------------------------------------------------------------------
    // CSV parsing (RFC 4180, handles quoted newlines and escaped quotes)
    // -------------------------------------------------------------------------

    private static List<List<String>> parse(String csvText) {
        try (BufferedReader reader = new BufferedReader(new StringReader(csvText))) {
            List<List<String>> rows = new ArrayList<>();
            StringBuilder record = new StringBuilder();
            int quoteCount = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (record.length() > 0) {
                    record.append('\n');
                }
                record.append(line);
                quoteCount += countQuotes(line);
                if (quoteCount % 2 == 0) {
                    rows.add(parseRecord(record.toString()));
                    record.setLength(0);
                    quoteCount = 0;
                }
            }
            if (record.length() > 0) {
                rows.add(parseRecord(record.toString()));
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read CSV", e);
        }
    }

    private static List<String> parseRecord(String record) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < record.length(); i++) {
            char ch = record.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < record.length() && record.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(ch);
            }
        }
        values.add(value.toString());
        return values;
    }

    private static int countQuotes(String text) {
        int count = 0;
        for (char ch : text.toCharArray()) {
            if (ch == '"') count++;
        }
        return count;
    }

    // -------------------------------------------------------------------------
    // CSV writing helpers
    // -------------------------------------------------------------------------

    private static void appendRow(StringBuilder csv, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) csv.append(',');
            csv.append(escape(values.get(i)));
        }
        csv.append("\r\n"); // Windows-compatible line endings
    }

    private static String escape(String value) {
        String safe = value(value);
        if (safe.contains(",") || safe.contains("\"")
                || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private static Map<String, String> rowMap(List<String> headers, List<String> values) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            row.put(headers.get(i), i < values.size() ? values.get(i).trim() : "");
        }
        return row;
    }

    private static String first(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String val = row.get(normalizeHeader(key));
            if (val != null && !val.isBlank()) return val;
        }
        return "";
    }

    private static boolean parseFavorite(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.equals("true") || v.equals("1") || v.equals("yes");
    }

    private static String normalizeHeader(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT)
                .replace("_", " ")
                .replace("-", " ")
                .replaceAll("\\s+", " ");
    }

    private static String value(String v) {
        return v == null ? "" : v;
    }

    private static String value(String preferred, String fallback) {
        return (preferred == null || preferred.isBlank()) ? value(fallback) : preferred;
    }

    // -------------------------------------------------------------------------
    // Functional interface
    // -------------------------------------------------------------------------

    @FunctionalInterface
    public interface PasswordResolver {
        String resolvePassword(Credential credential);
    }
}