package com.pollpulse.model;

// Sent back after register / login: the session token plus the user's public details.
public class AuthResponse {

    private final String token;
    private final User user;

    public AuthResponse(String token, User user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() { return token; }
    public User getUser() { return user; }
}
