package com.himalayanvault.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Password breach checker tests")
class PasswordBreachCheckerTest {

    @Test
    @DisplayName("hashes passwords with uppercase SHA-1")
    void hashesPassword() {
        assertEquals("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8", PasswordBreachChecker.sha1Hex("password"));
    }

    @Test
    @DisplayName("finds breach count by suffix")
    void findsBreachCount() {
        String response = """
                003D68EB55068C33ACE09247EE4C639306B:2
                1E4C9B93F3F0682250B6CF8331B7EE68FD8:12431
                2F1A392BFD9B1B85B18DB3CFE22608FDC4D:7
                """;

        assertEquals(12431, PasswordBreachChecker.findBreachCount(
                response,
                "1E4C9B93F3F0682250B6CF8331B7EE68FD8"));
    }

    @Test
    @DisplayName("returns zero when suffix is absent")
    void returnsZeroForMissingSuffix() {
        assertEquals(0, PasswordBreachChecker.findBreachCount("ABC:12", "DEF"));
    }
}
