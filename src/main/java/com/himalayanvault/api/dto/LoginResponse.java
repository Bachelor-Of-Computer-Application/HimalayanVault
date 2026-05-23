package com.himalayanvault.api.dto;

public class LoginResponse {
    public boolean success;
    public String token;
    public String message;
    
    public LoginResponse(boolean success, String token, String message) {
        this.success = success;
        this.token = token;
        this.message = message;
    }
}
