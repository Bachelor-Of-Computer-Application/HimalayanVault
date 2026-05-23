package com.himalayanvault.models;

/**
 * Credential — represents a stored credential for a website/service.
 */
public class Credential {
    
    private long id;
    private String ownerUsername;
    private String siteUrl;
    private String siteName;
    private String siteUsername;
    private String encryptedPassword;  // AES-GCM encrypted
    private String notes;
    private String createdAt;
    private String updatedAt;

    public Credential(long id, String ownerUsername, String siteUrl, String siteName,
                     String siteUsername, String encryptedPassword, String notes,
                     String createdAt, String updatedAt) {
        this.id = id;
        this.ownerUsername = ownerUsername;
        this.siteUrl = siteUrl;
        this.siteName = siteName;
        this.siteUsername = siteUsername;
        this.encryptedPassword = encryptedPassword;
        this.notes = notes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters
    public long getId() { return id; }
    public String getOwnerUsername() { return ownerUsername; }
    public String getSiteUrl() { return siteUrl; }
    public String getSiteName() { return siteName; }
    public String getSiteUsername() { return siteUsername; }
    public String getEncryptedPassword() { return encryptedPassword; }
    public String getNotes() { return notes; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }

    // Setters
    public void setSiteUrl(String siteUrl) { this.siteUrl = siteUrl; }
    public void setSiteName(String siteName) { this.siteName = siteName; }
    public void setSiteUsername(String siteUsername) { this.siteUsername = siteUsername; }
    public void setEncryptedPassword(String encryptedPassword) { this.encryptedPassword = encryptedPassword; }
    public void setNotes(String notes) { this.notes = notes; }
}
