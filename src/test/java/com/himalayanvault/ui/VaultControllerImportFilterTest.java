package com.himalayanvault.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javafx.stage.FileChooser;

@DisplayName("Vault Import Dialog Tests")
class VaultControllerImportFilterTest {

    @Test
    @DisplayName("Import dialog offers encrypted backup and CSV file types")
    void importDialogOffersHvltAndCsvFilters() {
        List<FileChooser.ExtensionFilter> filters = VaultController.importExtensionFilters();

        assertEquals(3, filters.size());
        assertTrue(filters.stream().anyMatch(filter ->
                filter.getExtensions().contains("*.hvlt") && filter.getExtensions().contains("*.csv")));
        assertTrue(filters.stream().anyMatch(filter ->
                VaultController.HVLT_IMPORT_DESCRIPTION.equals(filter.getDescription())
                        && filter.getExtensions().equals(List.of("*.hvlt"))));
        assertTrue(filters.stream().anyMatch(filter ->
                VaultController.CSV_IMPORT_DESCRIPTION.equals(filter.getDescription())
                        && filter.getExtensions().equals(List.of("*.csv"))));
    }
}
