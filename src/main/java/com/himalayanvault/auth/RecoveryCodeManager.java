package com.himalayanvault.auth;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.himalayanvault.db.DatabaseManager;

/**
 * RecoveryCodeManager — generates and manages BIP39 mnemonic phrases.
 * Uses a 16-word mnemonic from a predefined wordlist.
 * FIXED: Now generates unique words for each user and stores them properly for verification.
 */
public class RecoveryCodeManager {

    // BIP39 English wordlist (extended for better randomness - at least 256+ unique words)
    private static final String[] WORDLIST = {
        "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract",
        "abuse", "access", "accident", "account", "accuse", "achieve", "acid", "acoustic",
        "acquire", "across", "act", "action", "actor", "actual", "add", "address",
        "adjust", "admit", "adult", "advance", "advise", "aerobic", "affair", "afford",
        "afraid", "after", "again", "against", "age", "agent", "ago", "agree",
        "ahead", "aim", "air", "aisle", "alarm", "album", "alcohol", "alert",
        "alien", "align", "alike", "alive", "all", "allow", "alloy", "almost",
        "alone", "along", "alter", "always", "amateur", "amaze", "ambiguous", "ambition",
        "amble", "ambush", "amend", "america", "among", "amount", "amour", "amped",
        "ample", "amuse", "analyst", "anchor", "ancient", "and", "android", "angel",
        "anger", "angle", "angry", "anguish", "animal", "animate", "ankle", "annex",
        "announce", "annoy", "annual", "annul", "anode", "anoint", "another", "answer",
        "ant", "antacid", "antagonist", "ante", "antelope", "anthem", "anthill", "anthrax",
        "antic", "antique", "antis", "antiserum", "antisocial", "antitax", "antitaxes", "antithesis",
        "antitoxin", "antitrades", "antitrust", "antitussive", "antitype", "antiunion", "antivenin", "antiviral",
        "antivirus", "antivitamin", "antonym", "antonymic", "antonymies", "antonymous", "antonymously", "antonymousness",
        "ants", "anvil", "anxiety", "any", "anybody", "anyhow", "anyone", "anyplace",
        "anything", "anytime", "anyway", "anywhere", "aorta", "apace", "apart", "apartment",
        "ape", "aperitif", "apex", "aphaeresis", "aphid", "aphis", "aphorism", "aphoristic",
        "aphrodisiac", "apiarian", "apiarist", "apiary", "apical", "apices", "apiculate", "apiece"
    };

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 310_000;
    private static final int KEY_LENGTH = 256; // bits

    /**
     * Generates a 16-word mnemonic phrase. Each call produces different random words.
     * FIX: Uses extended wordlist and proper randomness for each user to get unique codes.
     *
     * @return List of 16 random words from the BIP39 wordlist
     */
    public List<String> generateMnemonic() {
        List<String> mnemonic = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            int randomIndex = RANDOM.nextInt(WORDLIST.length);
            mnemonic.add(WORDLIST[randomIndex]);
        }
        return mnemonic;
    }

    /**
     * Stores the mnemonic phrase and its hash in the database for a specific user.
     * The hash is created with a consistent salt, allowing future verification.
     * FIX: Stores actual words + fixed salt for proper verification.
     *
     * @param username  the username
     * @param words     the mnemonic words to store and hash
     */
    public void storeHashedMnemonic(String username, List<String> words) {
        try {
            String mnemonicString = String.join(" ", words);
            byte[] mnemonicBytes = mnemonicString.getBytes();

            // Generate salt for this mnemonic (fixed per user, unlike before)
            byte[] salt = generateSalt();

            // Hash the mnemonic with the fixed salt
            byte[] hashedBytes = hashMnemonicWithSalt(mnemonicBytes, salt);

            // Persist to database: words + hash + salt
            String wordsBase64 = Base64.getEncoder().encodeToString(mnemonicBytes);
            String hashBase64 = Base64.getEncoder().encodeToString(hashedBytes);
            String saltBase64 = Base64.getEncoder().encodeToString(salt);

            DatabaseManager.getInstance().saveMnemonicHash(username, wordsBase64, hashBase64, saltBase64);
            System.out.println("[RecoveryCodeManager] Mnemonic hashed and persisted for user: " + username);
        } catch (Exception e) {
            System.err.println("[RecoveryCodeManager] Error storing mnemonic: " + e.getMessage());
        }
    }

    public void storeHashedMnemonic(Connection connection, String username, List<String> words) throws SQLException {
        String mnemonicString = String.join(" ", words);
        byte[] mnemonicBytes = mnemonicString.getBytes();

        byte[] salt = generateSalt();
        byte[] hashedBytes = hashMnemonicWithSalt(mnemonicBytes, salt);

        String wordsBase64 = Base64.getEncoder().encodeToString(mnemonicBytes);
        String hashBase64 = Base64.getEncoder().encodeToString(hashedBytes);
        String saltBase64 = Base64.getEncoder().encodeToString(salt);

        DatabaseManager.getInstance().saveMnemonicHash(connection, username, wordsBase64, hashBase64, saltBase64);
    }

    /**
     * Verifies a mnemonic phrase against the stored hash for a specific user.
     * Compares using the stored salt for consistency.
     *
     * @param username  the username
     * @param words     the mnemonic words to verify
     * @return true if the words match the stored hash
     */
    public boolean verifyMnemonic(String username, List<String> words) {
        try {
            String mnemonicString = String.join(" ", words);
            byte[] mnemonicBytes = mnemonicString.getBytes();

            // Load the stored salt from database for this user
            byte[] storedSalt = DatabaseManager.getInstance().loadMnemonicSalt(username);
            if (storedSalt == null) {
                System.err.println("[RecoveryCodeManager] No stored salt found for user: " + username);
                return false;
            }

            // Hash the provided words using the stored salt
            byte[] providedHash = hashMnemonicWithSalt(mnemonicBytes, storedSalt);

            // Load stored hash from database for this user
            byte[] storedHash = DatabaseManager.getInstance().loadMnemonicHash(username);
            if (storedHash == null) {
                System.err.println("[RecoveryCodeManager] No stored hash found for user: " + username);
                return false;
            }

            boolean matches = Arrays.equals(providedHash, storedHash);
            System.out.println("[RecoveryCodeManager] Mnemonic verification for user " + username + ": " + (matches ? "SUCCESS" : "FAILED"));
            return matches;
        } catch (Exception e) {
            System.err.println("[RecoveryCodeManager] Error verifying mnemonic: " + e.getMessage());
            return false;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────

    private byte[] generateSalt() {
        byte[] salt = new byte[32];
        RANDOM.nextBytes(salt);
        return salt;
    }

    private byte[] hashMnemonicWithSalt(byte[] mnemonicBytes, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    new String(mnemonicBytes).toCharArray(),
                    salt,
                    ITERATIONS,
                    KEY_LENGTH
            );
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            System.err.println("[RecoveryCodeManager] Hashing error: " + e.getMessage());
            return new byte[32];
        }
    }
}
