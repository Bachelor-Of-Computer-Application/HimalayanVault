package com.himalayanvault.api.dto;

import java.util.List;

public class SignupResponse {
    public boolean success;
    public String message;
    public List<String> recoveryWords;

    public SignupResponse(boolean success, String message, List<String> recoveryWords) {
        this.success = success;
        this.message = message;
        this.recoveryWords = recoveryWords;
    }
}
