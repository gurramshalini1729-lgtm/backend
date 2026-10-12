package com.pollpulse.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

// A registered account. passwordHash is NEVER sent to React (@JsonIgnore).
public class User {

    private Long id;
    private String username;
    private String email;
    @JsonIgnore
    private String passwordHash;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}
