package com.himalayanvault.export;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import com.google.gson.Gson;
import com.himalayanvault.export.VaultExporter.ExportPayload;
import com.himalayanvault.models.Credential;

/**
 * Decrypts vault exports, verifies integrity, and prepares non-destructive
 * merge operations.
 */
public class VaultImporter {

    private static final int SALT_SIZE = 32;
    private static final int NONCE_SIZE = 12;
    private static final int CHECKSUM_SIZE = 32;
    private static final int TAG_SIZE = 128;
    private static final String CIPHER = "AES/GCM/NoPadding";

    private final Gson gson = new Gson();

    public ImportValidation validateFile(String encryptedFileBase64) {
        try {
            ParsedContainer container = parseContainer(encryptedFileBase64);
            return new ImportValidation(true, "File is valid", container.method, checksumFor(encryptedFileBase64));
        } catch (RuntimeException e) {
            return new ImportValidation(false, e.getMessage(), (byte) 0, null);
        }
    }

    public ImportPreview getImportPreviewWithPassphrase(String encryptedFileBase64, String passphrase,
                                                        List<Credential> existingCredentials) {
        ParsedContainer container = parseContainer(encryptedFileBase64);
        SecretKey key = VaultExporter.deriveKeyFromPassphrase(passphrase, container.salt);
        return buildPreview(decrypt(container, key), existingCredentials);
    }

    public ImportPreview getImportPreviewWithMnemonic(String encryptedFileBase64, String mnemonic,
                                                      List<Credential> existingCredentials) {
        ParsedContainer container = parseContainer(encryptedFileBase64);
        SecretKey key = VaultExporter.deriveKeyFromMnemonic(mnemonic);
        return buildPreview(decrypt(container, key), existingCredentials);
    }

    public ImportPreview getImportPreview(String encryptedFileBase64, SecretKey decryptionKey) {
        return buildPreview(decrypt(parseContainer(encryptedFileBase64), decryptionKey), List.of());
    }

    public MergeResult importWithPassphrase(String encryptedFileBase64, String passphrase,
                                            List<Credential> existingCredentials) {
        ParsedContainer container = parseContainer(encryptedFileBase64);
        SecretKey key = VaultExporter.deriveKeyFromPassphrase(passphrase, container.salt);
        return importWithMerge(container, key, existingCredentials);
    }

    public MergeResult importWithMnemonic(String encryptedFileBase64, String mnemonic,
                                          List<Credential> existingCredentials) {
        ParsedContainer container = parseContainer(encryptedFileBase64);
        SecretKey key = VaultExporter.deriveKeyFromMnemonic(mnemonic);
        return importWithMerge(container, key, existingCredentials);
    }

    public MergeResult importWithMerge(String encryptedFileBase64, SecretKey decryptionKey,
                                       List<Credential> existingCredentials) {
        return importWithMerge(parseContainer(encryptedFileBase64), decryptionKey, existingCredentials);
    }

    private MergeResult importWithMerge(ParsedContainer container, SecretKey decryptionKey,
                                        List<Credential> existingCredentials) {
        ExportPayload payload = decrypt(container, decryptionKey);
        List<Credential> imported = payload.credentials != null ? payload.credentials : List.of();

        Map<String, Credential> existingByKey = new HashMap<>();
        for (Credential credential : existingCredentials) {
            existingByKey.put(matchKey(credential), credential);
        }

        List<Credential> merged = new ArrayList<>(existingCredentials);
        int added = 0;
        int updated = 0;

        for (Credential importedCredential : imported) {
            Credential existing = existingByKey.get(matchKey(importedCredential));
            if (existing == null) {
                merged.add(importedCredential);
                added++;
            } else {
                int index = merged.indexOf(existing);
                importedCredential.id = existing.id;
                importedCredential.ownerUsername = existing.ownerUsername;
                merged.set(index, importedCredential);
                updated++;
            }
        }

        return new MergeResult(merged, added, updated, imported.size());
    }

    private ImportPreview buildPreview(ExportPayload payload, List<Credential> existingCredentials) {
        List<Credential> imported = payload.credentials != null ? payload.credentials : List.of();
        Map<String, Credential> existingByKey = new HashMap<>();
        for (Credential credential : existingCredentials) {
            existingByKey.put(matchKey(credential), credential);
        }

        List<ImportAction> actions = new ArrayList<>();
        for (Credential credential : imported) {
            actions.add(new ImportAction(
                    credential.siteUrl,
                    credential.siteUsername,
                    existingByKey.containsKey(matchKey(credential)) ? "UPDATE" : "ADD"));
        }

        return new ImportPreview(
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(payload.timestamp)),
                payload.comment,
                imported.size(),
                actions);
    }

    private ExportPayload decrypt(ParsedContainer container, SecretKey key) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_SIZE, container.nonce));
            byte[] compressed = cipher.doFinal(container.ciphertext);

            byte[] calculatedChecksum = MessageDigest.getInstance("SHA-256").digest(compressed);
            if (!Arrays.equals(container.payloadChecksum, calculatedChecksum)) {
                throw new IllegalStateException("Checksum mismatch. The export file may be corrupted.");
            }

            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                String json = new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
                return gson.fromJson(json, ExportPayload.class);
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Import failed. The key may be wrong or the file may be corrupted.", e);
        }
    }

    private ParsedContainer parseContainer(String encryptedFileBase64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encryptedFileBase64);
            int minimumSize = 4 + 1 + 1 + SALT_SIZE + NONCE_SIZE + CHECKSUM_SIZE + 16;
            if (bytes.length < minimumSize) {
                throw new IllegalArgumentException("Export file is too small");
            }

            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            byte[] magic = new byte[4];
            buffer.get(magic);
            if (!VaultExporter.MAGIC.equals(new String(magic, StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("Invalid export file format");
            }

            int version = buffer.get() & 0xFF;
            if (version != VaultExporter.VERSION) {
                throw new IllegalArgumentException("Unsupported export version: " + version);
            }

            ParsedContainer container = new ParsedContainer();
            container.method = buffer.get();
            container.salt = read(buffer, SALT_SIZE);
            container.nonce = read(buffer, NONCE_SIZE);
            container.payloadChecksum = read(buffer, CHECKSUM_SIZE);
            container.ciphertext = read(buffer, buffer.remaining());
            return container;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid export file", e);
        }
    }

    private static byte[] read(ByteBuffer buffer, int size) {
        byte[] bytes = new byte[size];
        buffer.get(bytes);
        return bytes;
    }

    private static String checksumFor(String encryptedFileBase64) {
        return VaultExporter.sha256Base64(Base64.getDecoder().decode(encryptedFileBase64));
    }

    private static String matchKey(Credential credential) {
        return normalize(credential.siteUrl) + "|" + normalize(credential.siteUsername);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static class ParsedContainer {
        byte method;
        byte[] salt;
        byte[] nonce;
        byte[] payloadChecksum;
        byte[] ciphertext;
    }

    public static class ImportValidation {
        public boolean valid;
        public String message;
        public byte method;
        public String checksum;

        public ImportValidation(boolean valid, String message, byte method, String checksum) {
            this.valid = valid;
            this.message = message;
            this.method = method;
            this.checksum = checksum;
        }
    }

    public static class ImportPreview {
        public String backupTimestamp;
        public String backupComment;
        public int credentialCount;
        public List<ImportAction> actions;

        public ImportPreview(String timestamp, String comment, int count, List<ImportAction> actions) {
            this.backupTimestamp = timestamp;
            this.backupComment = comment;
            this.credentialCount = count;
            this.actions = actions;
        }
    }

    public static class ImportAction {
        public String siteUrl;
        public String username;
        public String action;

        public ImportAction(String siteUrl, String username, String action) {
            this.siteUrl = siteUrl;
            this.username = username;
            this.action = action;
        }
    }

    public static class MergeResult {
        public List<Credential> mergedCredentials;
        public int credentialsAdded;
        public int credentialsUpdated;
        public int credentialsImported;

        public MergeResult(List<Credential> merged, int added, int updated, int imported) {
            this.mergedCredentials = merged;
            this.credentialsAdded = added;
            this.credentialsUpdated = updated;
            this.credentialsImported = imported;
        }
    }
}
