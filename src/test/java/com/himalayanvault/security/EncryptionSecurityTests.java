package com.himalayanvault.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Encryption & Security Tests")
public class EncryptionSecurityTests {

    private final String testPassword = "TestPassword123!@#";
    private final String testData = "This is sensitive credential data";

    @Test
    @DisplayName("PBKDF2: Hash and verify password")
    void testPbkdf2HashAndVerify() {
        String hash = Pbkdf2PasswordHasher.hashPassword(testPassword);
        assertNotNull(hash);
        assertTrue(hash.startsWith("pbkdf2-sha256$310000$"));
        assertTrue(Pbkdf2PasswordHasher.verifyPassword(hash, testPassword));
        assertFalse(Pbkdf2PasswordHasher.verifyPassword(hash, "WrongPassword"));
    }

    @Test
    @DisplayName("PBKDF2: Unique salts produce different hashes")
    void testPbkdf2UniqueSalts() {
        String hash1 = Pbkdf2PasswordHasher.hashPassword(testPassword);
        String hash2 = Pbkdf2PasswordHasher.hashPassword(testPassword);
        assertNotEquals(hash1, hash2);
    }

    @Test
    @DisplayName("PBKDF2: Current hash does not need rehash")
    void testPbkdf2NeedsRehashing() {
        String hash = Pbkdf2PasswordHasher.hashPassword(testPassword);
        assertFalse(Pbkdf2PasswordHasher.needsRehash(hash));
        assertTrue(Pbkdf2PasswordHasher.needsRehash("pbkdf2-sha256$100000$abc$def"));
    }

    @Test
    @DisplayName("AES-GCM: Encrypt and decrypt with derived key")
    void testAESEncryptDecryptWithDerivedKey() {
        byte[] masterSalt = new byte[32];
        new java.security.SecureRandom().nextBytes(masterSalt);

        javax.crypto.SecretKey key = EncryptionUtil.deriveKeyFromPassword(testPassword, masterSalt);
        String encrypted = EncryptionUtil.encrypt(testData, key);

        assertNotNull(encrypted);
        assertNotEquals(testData, encrypted);
        assertEquals(testData, EncryptionUtil.decrypt(encrypted, key));
    }

    @Test
    @DisplayName("AES-GCM: Each encryption produces different ciphertext")
    void testAESEncryptionRandomness() {
        byte[] masterSalt = new byte[32];
        new java.security.SecureRandom().nextBytes(masterSalt);
        javax.crypto.SecretKey key = EncryptionUtil.deriveKeyFromPassword(testPassword, masterSalt);

        assertNotEquals(EncryptionUtil.encrypt(testData, key), EncryptionUtil.encrypt(testData, key));
    }

    @Test
    @DisplayName("AES-GCM: Decryption with wrong key fails")
    void testAESWrongKeyDecryption() {
        byte[] salt1 = new byte[32];
        byte[] salt2 = new byte[32];
        new java.security.SecureRandom().nextBytes(salt1);
        new java.security.SecureRandom().nextBytes(salt2);

        javax.crypto.SecretKey key1 = EncryptionUtil.deriveKeyFromPassword(testPassword, salt1);
        javax.crypto.SecretKey key2 = EncryptionUtil.deriveKeyFromPassword(testPassword, salt2);
        String encrypted = EncryptionUtil.encrypt(testData, key1);

        assertThrows(RuntimeException.class, () -> EncryptionUtil.decrypt(encrypted, key2));
    }

    @Test
    @DisplayName("Session: Create, validate, and lock session")
    void testSessionCreateValidateAndLock() {
        SessionManager manager = SessionManager.getInstance();
        manager.invalidateAllSessions();

        String token = manager.createSession("testuser", testPassword, new byte[32]);
        assertNotNull(token);
        assertFalse(manager.isLocked());
        assertTrue(manager.isValidToken(token));
        assertEquals("testuser", manager.getUsernameFromToken(token));

        manager.lock();
        assertTrue(manager.isLocked());
        assertEquals(0, manager.getActiveSessionCount());
        assertFalse(manager.isValidToken(token));
    }

    @Test
    @DisplayName("Password Generator: Generate valid password")
    void testPasswordGeneration() {
        String password = EncryptionUtil.generatePassword(16, true, true, true, true);
        assertNotNull(password);
        assertEquals(16, password.length());
    }
}
