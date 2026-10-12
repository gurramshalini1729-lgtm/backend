package com.pollpulse.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// A poll with its options. Spring converts this object to JSON automatically.
public class Poll {

    private Long id;
    private String question;
    private String category;
    private String creator;          // username of the creator (null for old sample polls)
    private LocalDateTime createdAt;
    private String expiresAt;        // ISO UTC like "2026-10-20T10:00:00Z", or null
    private boolean closed;          // true when expiresAt has passed
    private int totalVotes;
    private Long yourOptionId;       // option the signed-in user voted for (null if not voted / not signed in)
    private List<PollOption> options = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }

    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }

    public int getTotalVotes() { return totalVotes; }
    public void setTotalVotes(int totalVotes) { this.totalVotes = totalVotes; }

    public Long getYourOptionId() { return yourOptionId; }
    public void setYourOptionId(Long yourOptionId) { this.yourOptionId = yourOptionId; }

    public List<PollOption> getOptions() { return options; }
    public void setOptions(List<PollOption> options) { this.options = options; }
}
