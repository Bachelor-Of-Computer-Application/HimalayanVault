package com.himalayanvault.export;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.himalayanvault.models.Credential;

@DisplayName("Vault Export/Import Tests")
public class VaultExportImportTests {

    @Test
    @DisplayName("Passphrase export imports with checksum verification")
    void testPassphraseExportImport() {
        VaultExporter exporter = new VaultExporter();
        VaultImporter importer = new VaultImporter();

        VaultExporter.ExportResult result = exporter.exportWithPassphrase(credentials(), "export-passphrase", "test");
        assertNotNull(result.encryptedFile);
        assertNotNull(result.checksum);

        VaultImporter.ImportPreview preview =
                importer.getImportPreviewWithPassphrase(result.encryptedFile, "export-passphrase", List.of());
        assertEquals(2, preview.credentialCount);
        assertEquals("https://example.com", preview.actions.get(0).siteUrl);
        assertEquals("alice", preview.actions.get(0).username);
    }

    @Test
    @DisplayName("Mnemonic export generates valid 12-word BIP39 and decrypts")
    void testMnemonicExportImport() {
        VaultExporter exporter = new VaultExporter();
        VaultImporter importer = new VaultImporter();

        VaultExporter.ExportResult result = exporter.exportWithBIP39Mnemonic(credentials(), "mnemonic");
        assertNotNull(result.bip39Mnemonic);
        assertEquals(12, result.bip39Mnemonic.split("\\s+").length);

        VaultImporter.ImportPreview preview =
                importer.getImportPreviewWithMnemonic(result.encryptedFile, result.bip39Mnemonic, List.of());
        assertEquals(2, preview.credentialCount);
    }

    @Test
    @DisplayName("Checksum verification rejects corrupted exports")
    void testChecksumVerificationRejectsCorruption() {
        VaultExporter exporter = new VaultExporter();
        VaultImporter importer = new VaultImporter();
        VaultExporter.ExportResult result = exporter.exportWithBIP39Mnemonic(credentials(), "tamper");

        byte[] bytes = Base64.getDecoder().decode(result.encryptedFile);
        bytes[bytes.length - 1] ^= 0x01;
        String corrupted = Base64.getEncoder().encodeToString(bytes);

        assertThrows(IllegalStateException.class, 
                () -> importer.getImportPreviewWithMnemonic(corrupted, result.bip39Mnemonic, List.of()));
    }

    @Test
    @DisplayName("Merge import updates URL+username matches and keeps existing credentials")
    void testMergeImport() {
        VaultExporter exporter = new VaultExporter();
        VaultImporter importer = new VaultImporter();

        List<Credential> existing = new ArrayList<>();
        existing.add(credential("https://example.com", "alice", "old"));
        existing.add(credential("https://keep.local", "bob", "keep"));

        VaultExporter.ExportResult result = exporter.exportWithBIP39Mnemonic(credentials(), "merge");
        VaultImporter.ImportPreview preview =
                importer.getImportPreviewWithMnemonic(result.encryptedFile, result.bip39Mnemonic, existing);
        assertTrue(preview.actions.stream().anyMatch(a -> "UPDATE".equals(a.action)));

        VaultImporter.MergeResult merge =
                importer.importWithMnemonic(result.encryptedFile, result.bip39Mnemonic, existing);
        assertEquals(1, merge.credentialsUpdated);
        assertEquals(1, merge.credentialsAdded);
        assertEquals(3, merge.mergedCredentials.size());
        assertFalse(merge.mergedCredentials.stream().noneMatch(c -> "https://keep.local".equals(c.siteUrl)));
    }

    @Test
    @DisplayName("CSV import maps Bitwarden login fields")
    void testBitwardenCsvImport() {
        String csv = """
                folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp
                Work,1,login,Example,"hello, note",,,https://example.com,alice,secret,
                """;

        List<Credential> imported = new CsvCredentialTransfer().importCsv(csv);

        assertEquals(1, imported.size());
        assertEquals("Example", imported.get(0).siteName);
        assertEquals("https://example.com", imported.get(0).siteUrl);
        assertEquals("alice", imported.get(0).siteUsername);
        assertEquals("secret", imported.get(0).encryptedPassword);
        assertEquals("Work", imported.get(0).category);
        assertTrue(imported.get(0).favorite);
    }

    @Test
    @DisplayName("CSV import maps LastPass fields")
    void testLastPassCsvImport() {
        String csv = """
                url,username,password,extra,name,grouping,fav
                https://vault.local,bob,p@ss,notes,Vault,Personal,0
                """;

        List<Credential> imported = new CsvCredentialTransfer().importCsv(csv);

        assertEquals(1, imported.size());
        assertEquals("Vault", imported.get(0).siteName);
        assertEquals("https://vault.local", imported.get(0).siteUrl);
        assertEquals("bob", imported.get(0).siteUsername);
        assertEquals("p@ss", imported.get(0).encryptedPassword);
        assertEquals("Personal", imported.get(0).category);
        assertFalse(imported.get(0).favorite);
    }

    @Test
    @DisplayName("CSV import maps 1Password fields")
    void testOnePasswordCsvImport() {
        String csv = """
                title,website,username,password,notes
                Portal,https://portal.local,dana,hunter2,"from 1Password"
                """;

        List<Credential> imported = new CsvCredentialTransfer().importCsv(csv);

        assertEquals(1, imported.size());
        assertEquals("Portal", imported.get(0).siteName);
        assertEquals("https://portal.local", imported.get(0).siteUrl);
        assertEquals("dana", imported.get(0).siteUsername);
        assertEquals("hunter2", imported.get(0).encryptedPassword);
        assertEquals("from 1Password", imported.get(0).notes);
    }

    @Test
    @DisplayName("CSV export writes plaintext migration rows with escaping")
    void testCsvExport() {
        CsvCredentialTransfer transfer = new CsvCredentialTransfer();
        Credential credential = credential("https://example.com", "alice", "stored-password");
        credential.siteName = "Example, Inc";
        credential.notes = "line 1\nline 2";

        String csv = transfer.exportCsv(List.of(credential), c -> "plain,password", null);

        assertTrue(csv.startsWith("name,loginUri,loginUsername,loginPassword,notes\n"));
        assertTrue(csv.contains("\"Example, Inc\""));
        assertTrue(csv.contains("\"plain,password\""));
        assertTrue(csv.contains("\"line 1\nline 2\""));
    }

    private static List<Credential> credentials() {
        return List.of(
                credential("https://example.com", "alice", "encrypted-1"),
                credential("https://new.example", "carol", "encrypted-2"));
    }

    private static Credential credential(String siteUrl, String username, String encryptedPassword) {
        Credential credential = new Credential();
        credential.ownerUsername = "owner";
        credential.siteUrl = siteUrl;
        credential.siteName = siteUrl;
        credential.siteUsername = username;
        credential.encryptedPassword = encryptedPassword;
        credential.notes = "note";
        credential.updated_at = System.currentTimeMillis();
        return credential;
    }
}
