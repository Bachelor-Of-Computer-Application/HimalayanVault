package com.himalayanvault.api.dto;

public class ErrorResponse {
    public boolean success = false;
    public String error;
    public String message;
    public int statusCode;
    
    public ErrorResponse(String error, String message, int statusCode) {
        this.error = error;
        this.message = message;
        this.statusCode = statusCode;
    }
}
