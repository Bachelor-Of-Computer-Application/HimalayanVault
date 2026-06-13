package com.himalayanvault.export;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.himalayanvault.models.Credential;

@DisplayName("CSV Credential Transfer Tests")
class CsvCredentialTransferTest {

    @Test
    @DisplayName("Exported CSV imports back into equivalent migration credentials")
    void csvExportImportRoundTrip() {
        CsvCredentialTransfer transfer = new CsvCredentialTransfer();
        Credential original = new Credential();
        original.siteName = "Example, Inc";
        original.siteUrl = "https://example.com/login";
        original.siteUsername = "alice@example.com";
        original.encryptedPassword = "stored-value";
        original.notes = "line 1\nline \"2\"";

        String exported = transfer.exportCsv(List.of(original), credential -> "plain,password");
        List<Credential> imported = transfer.importCsv(exported);

        assertEquals(1, imported.size());
        Credential roundTripped = imported.get(0);
        assertEquals(original.siteName, roundTripped.siteName);
        assertEquals(original.siteUrl, roundTripped.siteUrl);
        assertEquals(original.siteUsername, roundTripped.siteUsername);
        assertEquals("plain,password", roundTripped.encryptedPassword);
        assertEquals(original.notes, roundTripped.notes);
    }
}
