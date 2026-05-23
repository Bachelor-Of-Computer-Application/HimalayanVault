package com.himalayanvault.api.dto;

public class PasswordGenerateRequest {
    public int length = 16;
    public boolean useUppercase = true;
    public boolean useLowercase = true;
    public boolean useNumbers = true;
    public boolean useSpecialChars = true;
}
