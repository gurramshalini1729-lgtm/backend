package com.pollpulse.model;

import java.util.List;

// JSON sent by React when creating a poll:
// { "question": "...", "category": "Technology", "options": ["A", "B"], "expiresAt": "2026-10-20T10:00:00.000Z" }
// expiresAt is OPTIONAL (null = the poll never closes). It is an ISO-8601 UTC time.
public class CreatePollRequest {

    private String question;
    private String category;
    private List<String> options;
    private String expiresAt;

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }

    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
}
