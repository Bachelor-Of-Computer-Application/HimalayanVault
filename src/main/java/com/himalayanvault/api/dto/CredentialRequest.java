package com.himalayanvault.api.dto;

public class CredentialRequest {
    public String token;
    public String siteUrl;
    public String siteName;
    public String siteUsername;
    public String siteEmail;          // Optional email identifier
    public String encryptedPassword;  // AES-GCM encrypted
    public String notes;
    public String category;
    public String tags;
    public boolean favorite;
}
