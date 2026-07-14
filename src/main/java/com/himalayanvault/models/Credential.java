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
    public String siteEmail;               // Optional email identifier
    public int accountNumber = 1;          // Support multiple accounts per site
    public String encryptedPassword;       // AES-GCM encrypted
    public String notes;
    public String category;
    public String tags;
    public boolean favorite;
    public long created_at;                // Timestamp in milliseconds
    public long updated_at;                // Timestamp in milliseconds

    // Default constructor for JSON deserialization
    public Credential() {
    }

    public Credential(long id, String ownerUsername, String siteUrl, String siteName,
                     String siteUsername, String siteEmail, int accountNumber, String encryptedPassword, String notes,
                     String category, String tags, boolean favorite, long createdAt, long updatedAt) {
        this.id = id;
        this.ownerUsername = ownerUsername;
        this.siteUrl = siteUrl;
        this.siteName = siteName;
        this.siteUsername = siteUsername;
        this.siteEmail = siteEmail;
        this.accountNumber = accountNumber;
        this.encryptedPassword = encryptedPassword;
        this.notes = notes;
        this.category = category;
        this.tags = tags;
        this.favorite = favorite;
        this.created_at = createdAt;
        this.updated_at = updatedAt;
    }

    // Backward compatibility constructor (without siteEmail)
    public Credential(long id, String ownerUsername, String siteUrl, String siteName,
                     String siteUsername, int accountNumber, String encryptedPassword, String notes,
                     String category, String tags, boolean favorite, long createdAt, long updatedAt) {
        this(id, ownerUsername, siteUrl, siteName, siteUsername, "", accountNumber, encryptedPassword, notes,
                category, tags, favorite, createdAt, updatedAt);
    }

    public Credential(long id, String ownerUsername, String siteUrl, String siteName,
                     String siteUsername, int accountNumber, String encryptedPassword, String notes,
                     long createdAt, long updatedAt) {
        this(id, ownerUsername, siteUrl, siteName, siteUsername, "", accountNumber, encryptedPassword, notes,
                "", "", false, createdAt, updatedAt);
    }

    // Backward compatibility constructor (without accountNumber)
    public Credential(long id, String ownerUsername, String siteUrl, String siteName,
                     String siteUsername, String encryptedPassword, String notes,
                     long createdAt, long updatedAt) {
        this(id, ownerUsername, siteUrl, siteName, siteUsername, "", 1, encryptedPassword, notes,
                "", "", false, createdAt, updatedAt);
    }

    @Override
    public String toString() {
        return String.format("Credential{id=%d, site=%s, username=%s, account=%d, category=%s, favorite=%s}",
            id, siteUrl, siteUsername, accountNumber, category, favorite);
    }
}
