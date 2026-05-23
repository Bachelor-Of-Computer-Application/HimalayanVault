package com.himalayanvault.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * EncryptionUtil — handles AES-GCM encryption/decryption for credentials.
 * Uses 256-bit keys and 96-bit IVs for maximum security.
 */
public class EncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256; // bits
    private static final int IV_SIZE = 96;   // bits (12 bytes)
    private static final int TAG_SIZE = 128; // bits (16 bytes)

    /**
     * Generate a random AES key.
     *
     * @return a 256-bit AES key
     */
    public static SecretKey generateKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(KEY_SIZE);
            return keyGen.generateKey();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate AES key", e);
        }
    }

    /**
     * Derive an AES key from a master password using PBKDF2.
     * The user's master password is used to derive the key for encrypting credentials.
     *
     * @param masterPassword the master password
     * @param masterSalt     the salt from the vault (for consistency)
     * @return derived AES key
     */
    public static SecretKey deriveKeyFromPassword(String masterPassword, byte[] masterSalt) {
        try {
            // Use PBKDF2 to derive a 256-bit key
            javax.crypto.SecretKeyFactory skf = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                masterPassword.toCharArray(),
                masterSalt,
                100_000,  // iterations (same as AuthManager)
                KEY_SIZE
            );
            byte[] key = skf.generateSecret(spec).getEncoded();
            spec.clearPassword();
            return new SecretKeySpec(key, 0, key.length, "AES");
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive AES key from password", e);
        }
    }

    /**
     * Encrypt plaintext password using AES-GCM.
     *
     * @param plaintext the plaintext password
     * @param key       the AES key
     * @return encrypted password as Base64 (IV + ciphertext)
     */
    public static String encrypt(String plaintext, SecretKey key) {
        try {
            // Generate random IV
            byte[] iv = new byte[IV_SIZE / 8];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            // Create cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_SIZE, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            // Encrypt
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Combine IV and ciphertext
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypt AES-GCM encrypted password.
     *
     * @param encryptedBase64 the encrypted password as Base64 (IV + ciphertext)
     * @param key             the AES key
     * @return decrypted plaintext password
     */
    public static String decrypt(String encryptedBase64, SecretKey key) {
        try {
            // Decode Base64
            byte[] encrypted = Base64.getDecoder().decode(encryptedBase64);

            // Extract IV and ciphertext
            ByteBuffer buffer = ByteBuffer.wrap(encrypted);
            byte[] iv = new byte[IV_SIZE / 8];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            // Create cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_SIZE, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            // Decrypt
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /**
     * Generate a random password with specified criteria.
     *
     * @param length              the password length
     * @param useUppercase        include uppercase letters
     * @param useLowercase        include lowercase letters
     * @param useNumbers          include numbers
     * @param useSpecialChars     include special characters
     * @return generated password
     */
    public static String generatePassword(int length, boolean useUppercase, boolean useLowercase,
                                         boolean useNumbers, boolean useSpecialChars) {
        StringBuilder charset = new StringBuilder();
        
        if (useLowercase) {
            charset.append("abcdefghijklmnopqrstuvwxyz");
        }
        if (useUppercase) {
            charset.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        }
        if (useNumbers) {
            charset.append("0123456789");
        }
        if (useSpecialChars) {
            charset.append("!@#$%^&*()_+-=[]{}|;:,.<>?");
        }

        if (charset.length() == 0) {
            throw new IllegalArgumentException("At least one character set must be enabled");
        }

        StringBuilder password = new StringBuilder();
        SecureRandom random = new SecureRandom();
        
        for (int i = 0; i < length; i++) {
            password.append(charset.charAt(random.nextInt(charset.length())));
        }

        return password.toString();
    }
}
