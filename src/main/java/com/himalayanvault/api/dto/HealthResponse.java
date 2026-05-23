package com.himalayanvault.api.dto;

public class HealthResponse {
    public boolean status;
    public String message;
    public String timestamp;
    
    public HealthResponse(boolean status, String message, String timestamp) {
        this.status = status;
        this.message = message;
        this.timestamp = timestamp;
    }
}
