package com.himalayanvault.export;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.bitcoinj.crypto.MnemonicCode;

import com.google.gson.Gson;
import com.himalayanvault.models.Credential;
import com.himalayanvault.security.Pbkdf2PasswordHasher;

/**
 * Creates encrypted vault exports using keys that are independent from the
 * user's master-password verifier.
 */
public class VaultExporter {

    public static final String MAGIC = "HVLT";
    public static final int VERSION = 2;
    public static final byte METHOD_PASSPHRASE = 1;
    public static final byte METHOD_MNEMONIC = 2;

    private static final int SALT_SIZE = 32;
    private static final int NONCE_SIZE = 12;
    private static final int CHECKSUM_SIZE = 32;
    private static final int TAG_SIZE = 128;
    private static final int KEY_BITS = 256;
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final String KDF = "PBKDF2WithHmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Gson gson = new Gson();

    public ExportResult exportWithPassphrase(List<Credential> credentials, String userPassphrase, String exportComment) {
        if (userPassphrase == null || userPassphrase.isBlank()) {
            throw new IllegalArgumentException("Export passphrase is required");
        }

        byte[] salt = randomBytes(SALT_SIZE);
        SecretKey key = deriveKeyFromPassphrase(userPassphrase, salt);
        return exportWithKey(credentials, exportComment, key, salt, METHOD_PASSPHRASE, null);
    }

    public ExportResult exportWithBIP39Mnemonic(List<Credential> credentials, String exportComment) {
        byte[] entropy = randomBytes(16);
        List<String> words = MnemonicCode.INSTANCE.toMnemonic(entropy);
        String mnemonic = String.join(" ", words);
        SecretKey key = deriveKeyFromMnemonic(mnemonic);
        return exportWithKey(credentials, exportComment, key, randomBytes(SALT_SIZE), METHOD_MNEMONIC, mnemonic);
    }

    public SecretKey recoverKeyFromBIP39Mnemonic(String bip39Mnemonic) {
        return deriveKeyFromMnemonic(bip39Mnemonic);
    }

    public static SecretKey deriveKeyFromPassphrase(String passphrase, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, Pbkdf2PasswordHasher.ITERATIONS, KEY_BITS);
            try {
                byte[] key = SecretKeyFactory.getInstance(KDF).generateSecret(spec).getEncoded();
                return new SecretKeySpec(key, "AES");
            } finally {
                spec.clearPassword();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive export key from passphrase", e);
        }
    }

    public static SecretKey deriveKeyFromMnemonic(String mnemonic) {
        try {
            List<String> words = List.of(mnemonic.trim().toLowerCase().split("\\s+"));
            MnemonicCode.INSTANCE.check(words);
            byte[] seed = MnemonicCode.toSeed(words, "himalayan-vault-export");
            byte[] key = MessageDigest.getInstance("SHA-256").digest(seed);
            return new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid BIP39 mnemonic", e);
        }
    }

    private ExportResult exportWithKey(List<Credential> credentials, String exportComment, SecretKey key,
                                       byte[] salt, byte method, String mnemonic) {
        try {
            ExportPayload payload = new ExportPayload(credentials,
                    exportComment != null ? exportComment : "Backup from " + new java.util.Date());
            byte[] exportBytes = encryptAndPackage(payload, key, salt, method);
            String encryptedFile = Base64.getEncoder().encodeToString(exportBytes);
            String checksum = sha256Base64(exportBytes);
            return new ExportResult(encryptedFile, mnemonic, checksum, method);
        } catch (Exception e) {
            throw new IllegalStateException("Export failed", e);
        }
    }

    private byte[] encryptAndPackage(ExportPayload payload, SecretKey key, byte[] salt, byte method) throws Exception {
        byte[] compressed = gzip(gson.toJson(payload).getBytes(StandardCharsets.UTF_8));
        byte[] payloadChecksum = MessageDigest.getInstance("SHA-256").digest(compressed);
        byte[] nonce = randomBytes(NONCE_SIZE);

        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_SIZE, nonce));
        byte[] ciphertext = cipher.doFinal(compressed);

        ByteBuffer container = ByteBuffer.allocate(
                4 + 1 + 1 + salt.length + nonce.length + CHECKSUM_SIZE + ciphertext.length);
        container.put(MAGIC.getBytes(StandardCharsets.UTF_8));
        container.put((byte) VERSION);
        container.put(method);
        container.put(salt);
        container.put(nonce);
        container.put(payloadChecksum);
        container.put(ciphertext);
        return container.array();
    }

    private static byte[] gzip(byte[] payload) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(payload);
        }
        return out.toByteArray();
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    public static String sha256Base64(byte[] data) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException("Checksum generation failed", e);
        }
    }

    public static class ExportPayload {
        public String version = "2.0";
        public long timestamp = System.currentTimeMillis();
        public String comment = "";
        public List<Credential> credentials;

        public ExportPayload(List<Credential> credentials, String comment) {
            this.credentials = credentials;
            this.comment = comment;
        }
    }

    public static class ExportResult {
        public String encryptedFile;
        public String bip39Mnemonic;
        public String checksum;
        public byte method;

        public ExportResult(String encryptedFile, String mnemonic, String checksum, byte method) {
            this.encryptedFile = encryptedFile;
            this.bip39Mnemonic = mnemonic;
            this.checksum = checksum;
            this.method = method;
        }
    }
}
