package com.himalayanvault.api.dto;

import java.util.List;

import com.himalayanvault.models.Credential;

public class CredentialResponse {
    public boolean success;
    public String message;
    public Credential credential;
    public List<Credential> credentials;
    
    public CredentialResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
    
    public CredentialResponse(boolean success, String message, Credential credential) {
        this.success = success;
        this.message = message;
        this.credential = credential;
    }
    
    public CredentialResponse(boolean success, String message, List<Credential> credentials) {
        this.success = success;
        this.message = message;
        this.credentials = credentials;
    }
}
