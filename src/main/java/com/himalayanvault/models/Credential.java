package com.himalayanvault.models;

/**
 * Credential — represents a stored credential for a website/service.
 * Supports multiple accounts per site using accountNumber field.
 */
public class Credential {
    
    // Public fields for JSON serialization/export
    public long id;
    public String ownerUsername;
    public String siteUrl;
    public String siteName;
    public String siteUsername;
    public int accountNumber = 1;          // Support multiple accounts per site
    public String encryptedPassword;       // AES-GCM encrypted
    public String notes;
    public long created_at;                // Timestamp in milliseconds
    public long updated_at;                // Timestamp in milliseconds

    // Default constructor for JSON deserialization
    public Credential() {
    }

    public Credential(long id, String ownerUsername, String siteUrl, String siteName,
                     String siteUsername, int accountNumber, String encryptedPassword, String notes,
                     long createdAt, long updatedAt) {
        this.id = id;
        this.ownerUsername = ownerUsername;
        this.siteUrl = siteUrl;
        this.siteName = siteName;
        this.siteUsername = siteUsername;
        this.accountNumber = accountNumber;
        this.encryptedPassword = encryptedPassword;
        this.notes = notes;
        this.created_at = createdAt;
        this.updated_at = updatedAt;
    }

    // Backward compatibility constructor (without accountNumber)
    public Credential(long id, String ownerUsername, String siteUrl, String siteName,
                     String siteUsername, String encryptedPassword, String notes,
                     long createdAt, long updatedAt) {
        this(id, ownerUsername, siteUrl, siteName, siteUsername, 1, encryptedPassword, notes, createdAt, updatedAt);
    }

    @Override
    public String toString() {
        return String.format("Credential{id=%d, site=%s, username=%s, account=%d}", 
            id, siteUrl, siteUsername, accountNumber);
    }
}

