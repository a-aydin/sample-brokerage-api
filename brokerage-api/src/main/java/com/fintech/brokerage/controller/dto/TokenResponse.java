package com.fintech.brokerage.controller.dto;

public class TokenResponse {
    private String token;
    public TokenResponse(String token) { this.token = token; }
    public String getToken() { return token; }
}