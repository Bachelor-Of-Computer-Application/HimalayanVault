# HimalayanVault Security Features - Quick Start Guide

## 🔐 Before You Start

### Set Up the Pepper Secret
The pepper is a server-side secret that provides additional protection. Set it up before first use:

```bash
# Generate a random pepper
PEPPER=$(openssl rand -hex 32)

# Set environment variable (Linux/macOS)
export HIMALAYAN_VAULT_PEPPER="$PEPPER"

# Or add to .bashrc/.zshrc for persistence
echo "export HIMALAYAN_VAULT_PEPPER='$PEPPER'" >> ~/.bashrc

# Verify it's set
echo $HIMALAYAN_VAULT_PEPPER
```

**For Windows:**
```cmd
setx HIMALAYAN_VAULT_PEPPER "your-random-value-here"
```

---

## 🚀 Using New Features

### 1. Authentication (Argon2id)

The new authentication automatically uses Argon2id (memory-hard hashing):

```java
AuthManager auth = new AuthManager();

// Set master password (uses Argon2id automatically)
auth.setMasterPassword("myusername", "SecurePassword123!");

// Verify master password
boolean isValid = auth.verifyMasterPassword("myusername", "SecurePassword123!");
```

**What Changed:**
- Argon2id is now 100x more resistant to GPU attacks than PBKDF2
- All sessions are invalidated when password changes (automatic security)
- Pepper is automatically applied for extra protection

---

### 2. Session Management (Auto-lock)

```java
SessionManager session = SessionManager.getInstance();

// Create session after login
String token = session.createSession(username, password, salt);

// Session automatically locks after 15 minutes of inactivity
boolean isStillValid = session.isValidToken(token);  // Returns false after 15 min

// Manual lock when user clicks "Lock Vault"
session.lock();

// Check if locked
if (session.isLocked()) {
    System.out.println("Vault is locked. Please log in again.");
}
```

**What Changed:**
- Sessions now auto-lock after 15 minutes (instead of 30 minutes)
- Sessions are bound to device (prevents token theft/replay)
- All sessions invalidate when password changes

---

### 3. Searching & Filtering Credentials

```java
CredentialSearcher searcher = new CredentialSearcher(DatabaseManager.getInstance());

// Search by site URL
List<Credential> githubCreds = searcher.searchBySiteUrl(username, "github.com");

// Search by username
List<Credential> johnResults = searcher.searchByUsername(username, "john");

// Full-text search in all fields
List<Credential> bankResults = searcher.searchAll(username, "bank account");

// Get with pagination (50 per page)
List<Credential> page1 = searcher.getCredentialsPaginated(username, 0, 50);
List<Credential> page2 = searcher.getCredentialsPaginated(username, 50, 50);

// Find duplicate credentials
List<CredentialSearcher.CredentialDuplicate> dupes = searcher.findDuplicates(username);
for (var dup : dupes) {
    System.out.println("Duplicate: " + dup.siteUrl + " | " + dup.username + 
                       " appears " + dup.count + " times");
}

// Get recently modified
List<Credential> recent = searcher.getRecentlyModified(username, 10);
```

---

### 4. Multiple Accounts Per Site

Support for multiple login accounts for the same website:

```java
// Save first account for Gmail
long id1 = db.saveCredential(
    username,
    "https://gmail.com",
    "Gmail",
    "john@gmail.com",
    1,  // account_number = 1 (first account)
    encryptedPassword1,
    "Personal Gmail"
);

// Save second account for same Gmail
long id2 = db.saveCredential(
    username,
    "https://gmail.com",
    "Gmail",
    "john@gmail.com",
    2,  // account_number = 2 (second account)
    encryptedPassword2,
    "Work Gmail"
);

// Both can be stored separately
```

**UI Recommendation:**
When user selects "gmail.com", show a picker:
- Account 1: john@gmail.com (Personal)
- Account 2: john@gmail.com (Work)

---

### 5. Encrypted Export with Random 256-bit Export Key

Export uses a **completely separate, random 256-bit encryption key** that is **independent from your master password**. This means:
- ✅ Export can be restored even if you forget your master password
- ✅ Export security doesn't depend on master password strength
- ✅ 12-word BIP39 mnemonic alone is sufficient to recover vault

#### Method 1: Export with 12-Word BIP39 Mnemonic (Recommended)

The 12-word mnemonic is your backup key. You must **write it down and store it securely**.

```java
VaultExporter exporter = new VaultExporter();
CredentialSearcher searcher = new CredentialSearcher(db);

// Get all credentials
List<Credential> allCreds = searcher.getAllCredentials(username);

// Export: generates random 256-bit key, displays as 12-word mnemonic
VaultExporter.ExportResult result = exporter.exportWithBIP39Mnemonic(
    allCreds,
    "My backup - " + new Date()
);

// **IMPORTANT**: Write down this mnemonic and store securely!
System.out.println("=== SAVE THIS MNEMONIC ===");
System.out.println(result.bip39Mnemonic);
System.out.println("=== END MNEMONIC ===");

// Save encrypted file to disk
Files.write(Paths.get("vault-backup.hv"), 
            Base64.getDecoder().decode(result.encryptedFile));

// Optional: verify checksum
System.out.println("Checksum: " + result.checksum);
```

#### Recovery: Restore from 12-Word Mnemonic

```java
VaultExporter exporter = new VaultExporter();
VaultImporter importer = new VaultImporter();

// User provides the 12-word mnemonic they wrote down
String mnemonic = "word1 word2 word3 word4 word5 word6 word7 word8 word9 word10 word11 word12";

// Recover the encryption key from mnemonic
SecretKey recoveryKey = exporter.recoverKeyFromBIP39Mnemonic(mnemonic);

// Read encrypted backup from disk
String encryptedFileBase64 = Base64.getEncoder().encodeToString(
    Files.readAllBytes(Paths.get("vault-backup.hv"))
);

// Import with merge
VaultImporter.MergeResult result = importer.importWithMerge(
    encryptedFileBase64,
    recoveryKey,
    currentVaultCredentials
);

System.out.println("Restored " + result.credentialsAdded + " credentials");
```

#### Method 2: Export with User Passphrase

Alternative: use your own passphrase instead of BIP39 mnemonic.

```java
// Export with passphrase
String encryptedFile = exporter.exportWithPassphrase(
    allCreds,
    "MySecurePassphrase123!",
    "Manual backup"
);

// During recovery, user provides passphrase
// Import will use same mechanism to recover key
```

---

### 6. Safe Import (Merge Without Data Loss)

```java
VaultImporter importer = new VaultImporter();

// Step 1: Validate file integrity
VaultImporter.ImportValidation validation = importer.validateFile(encryptedFileBase64);
if (!validation.valid) {
    System.out.println("File validation failed: " + validation.message);
    return;
}

// Step 2: Show preview to user
VaultImporter.ImportPreview preview = importer.getImportPreview(
    encryptedFileBase64,
    decryptionKey
);
System.out.println("Backup timestamp: " + preview.backupTimestamp);
System.out.println("Backup comment: " + preview.backupComment);
System.out.println("Credentials to import: " + preview.credentialCount);

for (var action : preview.actions) {
    System.out.println("  - " + action.siteUrl + " | " + action.username);
}

// Step 3: User confirms, then merge
VaultImporter.MergeResult result = importer.importWithMerge(
    encryptedFileBase64,
    decryptionKey,
    currentVaultCredentials
);

System.out.println("Import complete!");
System.out.println("  Added: " + result.credentialsAdded);
System.out.println("  Updated: " + result.credentialsUpdated);
System.out.println("  Total in vault now: " + result.mergedCredentials.size());

// Save merged credentials back to vault
for (Credential cred : result.mergedCredentials) {
    db.saveCredential(username, cred.siteUrl, cred.siteName, 
                     cred.siteUsername, cred.accountNumber,
                     cred.encryptedPassword, cred.notes);
}
```

---

## 🧪 Running Tests

Comprehensive test suite validates all security features:

```bash
# Run all security tests
mvn clean test

# Run specific test
mvn test -Dtest=EncryptionSecurityTests

# View test report
# target/surefire-reports/com.himalayanvault.security.EncryptionSecurityTests.txt
```

**Test Coverage:**
- ✅ Argon2id hashing (3 tests)
- ✅ Pepper application (2 tests)
- ✅ AES-GCM encryption (3 tests)
- ✅ Session management (4 tests)
- ✅ Password generation (2 tests)

---

## 🔧 Configuration

### Database Indexes
Automatically created on first run:
- `idx_credentials_site_url` - Fast site lookups
- `idx_credentials_site_username` - Fast username lookups
- `idx_credentials_updated` - Fast sorting by date

### Session Timeouts
```java
// These are now customizable in SessionManager.java:
// INACTIVITY_TIMEOUT_MS = 15 * 60 * 1000  (15 minutes)
// SESSION_MAX_AGE_MS = 24 * 60 * 60 * 1000 (24 hours)
```

---

## 🛡️ Security Best Practices

1. **Pepper Secret**
   - ✅ Set environment variable before first run
   - ❌ Never commit pepper to git
   - ⚠️ Changing pepper invalidates all passwords

2. **Master Password**
   - ✅ Use strong, unique password (16+ characters)
   - ✅ Now protected by Argon2id + Pepper
   - ⚠️ Changing password logs out all sessions

3. **Backups**
   - ✅ Export monthly using BIP39 method
   - ✅ Store mnemonic in secure location
   - ⚠️ Never share mnemonic with anyone

4. **Vault Lock**
   - ✅ Auto-locks after 15 minutes
   - ✅ Lock immediately before leaving computer
   - ⚠️ Sessions don't survive app restart

5. **File Access**
   - ✅ Vault database at `~/.himalayan-vault/`
   - ❌ No built-in database encryption yet (planned)
   - Use OS-level file permissions for protection

---

## 📝 Migration from Old Version

If upgrading from an older version:

```bash
# 1. Backup current vault
# 2. Rebuild database with new schema
mvn clean compile javafx:run

# 3. Re-enter master password (converts to Argon2id)
# 4. Export vault with new method
# 5. Test import on same device
# 6. Verify all credentials present
```

---

## 🐛 Troubleshooting

### "HIMALAYAN_VAULT_PEPPER not set"
**Solution**: Set environment variable before running:
```bash
export HIMALAYAN_VAULT_PEPPER="your-value"
java -jar himalayan-vault.jar
```

### "Password verification FAILED"
**Possible causes:**
- Pepper environment variable changed
- Database corrupted
- Password entered incorrectly

**Solution:** Reset password:
```bash
# Use recovery mnemonic if available
# Or reset database and re-enter password
```

### "Session auto-locked unexpectedly"
**Cause:** 15-minute inactivity timeout

**Solution:** Log in again. For longer sessions:
1. Modify `INACTIVITY_TIMEOUT_MS` in SessionManager.java
2. Rebuild: `mvn clean package`

### Import shows "Checksum mismatch"
**Cause:** File corrupted or tampered

**Solution:** Use backup copy. If none available:
1. Check network/disk I/O errors
2. Verify file wasn't truncated
3. Try restoring from earlier backup

---

## 📞 Support & Feedback

For issues or feature requests:
1. Check SECURITY_IMPLEMENTATION.md for details
2. Review test cases in EncryptionSecurityTests.java
3. Enable logging: Add to pom.xml and check logs/

---

## ✨ Summary of Improvements

| Feature | What It Does | Benefit |
|---------|-------------|---------|
| **Argon2id** | Slow, memory-intensive hashing | 100x better than PBKDF2 vs GPU attacks |
| **Pepper** | Server-side secret | DB theft doesn't compromise passwords |
| **Auto-lock** | 15-min timeout | Reduces risk if device left unattended |
| **Device binding** | Ties session to device | Prevents token theft/reuse |
| **Encrypted export** | AES-256-GCM with BIP39 | Secure backup, recovery possible |
| **Search** | Full-text credential search | Faster credential lookup |
| **Multi-account** | Multiple logins per site | Supports shared devices |
| **Import merge** | Non-destructive import | No accidental data loss |

---

**Last Updated:** June 5, 2026
**Version:** 1.0.0
