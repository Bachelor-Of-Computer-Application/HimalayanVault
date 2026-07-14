package com.himalayanvault.api.dto;

public class CredentialUpdateRequest {
    public String token;
    public long credentialId;
    public String siteUrl;
    public String siteName;
    public String siteUsername;
    public String siteEmail;          // Optional email identifier
    public String encryptedPassword;
    public String notes;
    public String category;
    public String tags;
    public boolean favorite;
}
