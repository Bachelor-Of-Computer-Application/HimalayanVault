package com.himalayanvault.api.dto;

public class PasswordGenerateResponse {
    public boolean success;
    public String password;
    public String message;
    
    public PasswordGenerateResponse(boolean success, String password, String message) {
        this.success = success;
        this.password = password;
        this.message = message;
    }
}
