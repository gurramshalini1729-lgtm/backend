package com.pollpulse.service;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pollpulse.exception.ApiException;
import com.pollpulse.model.CreatePollRequest;
import com.pollpulse.model.Poll;
import com.pollpulse.model.PollOption;
import com.pollpulse.model.User;
import com.pollpulse.model.VoteRequest;
import com.pollpulse.repository.PollRepository;

// Business rules: validation, percentage calculation, transactions.
@Service
public class PollService {

    // Must match the list in frontend/src/services/categories.js
    private static final List<String> CATEGORIES =
            List.of("Technology", "Education", "Entertainment", "Sports", "Lifestyle", "Other");

    private final PollRepository pollRepository;

    public PollService(PollRepository pollRepository) {
        this.pollRepository = pollRepository;
    }

    public List<Poll> getAllPolls() {
        return pollRepository.findAllPolls();
    }

    // Poll + options (each option also carries its votes and percentage).
    // viewer = the signed-in user (or null) so we can tell React which option they chose.
    public Poll getPoll(long pollId, User viewer) {
        Poll poll = pollRepository.findPollById(pollId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Poll not found."));

        List<PollOption> options = pollRepository.findOptionsWithVoteCounts(pollId);

        // Total votes = sum of the votes of all options
        int total = 0;
        for (PollOption option : options) {
            total += option.getVoteCount();
        }

        // Percentage = option votes / total votes * 100, rounded to 1 decimal.
        // If total is 0 we use 0 so we never divide by zero.
        for (PollOption option : options) {
            double percentage = 0;
            if (total > 0) {
                percentage = Math.round(option.getVoteCount() * 1000.0 / total) / 10.0;
            }
            option.setPercentage(percentage);
        }

        poll.setTotalVotes(total);
        poll.setOptions(options);
        if (viewer != null) {
            poll.setYourOptionId(pollRepository.findVotedOption(pollId, viewer.getId()));
        }
        return poll;
    }

    // @Transactional: the poll and ALL its options are saved together.
    // If anything fails, MySQL rolls everything back (no half-saved poll).
    @Transactional
    public Poll createPoll(User creator, CreatePollRequest request) {
        validateNewPoll(request);
        String expiresAtUtc = parseExpiry(request.getExpiresAt());

        long pollId = pollRepository.insertPoll(request.getQuestion().trim(), request.getCategory(),
                creator.getId(), expiresAtUtc);
        for (String optionText : request.getOptions()) {
            pollRepository.insertOption(pollId, optionText.trim());
        }
        return getPoll(pollId, creator);
    }

    // Saves one vote for the signed-in user, then returns the updated results.
    // Rules enforced HERE and in MySQL (not in the browser): poll exists, option belongs to the poll,
    // poll not closed, and one vote per user per poll (UNIQUE key).
    public Poll vote(User user, long pollId, VoteRequest request) {
        if (request == null || request.getOptionId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please select an option to vote for.");
        }
        long optionId = request.getOptionId();

        if (!pollRepository.pollExists(pollId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Poll not found.");
        }
        if (!pollRepository.optionBelongsToPoll(pollId, optionId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That option does not belong to this poll.");
        }

        int rows;
        try {
            rows = pollRepository.insertVoteIfOpen(pollId, optionId, user.getId());
        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, "You have already voted in this poll.");
        }
        if (rows == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This poll has closed. Voting is no longer possible.");
        }
        return getPoll(pollId, user);
    }

    // "My Polls" page: polls I created and polls I voted in
    public Map<String, List<Poll>> getMyPolls(User user) {
        return Map.of("created", pollRepository.findPollsByCreator(user.getId()),
                      "voted", pollRepository.findPollsVotedBy(user.getId()));
    }

    // Only the creator may delete a poll (its options and votes are deleted with it)
    public void deletePoll(User user, long pollId) {
        requireCreator(user, pollId);
        pollRepository.deletePoll(pollId);
    }

    // Only the creator may close a poll early. Results stay visible, voting stops.
    public Poll closePoll(User user, long pollId) {
        requireCreator(user, pollId);
        if (pollRepository.closePollNow(pollId) == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This poll is already closed.");
        }
        return getPoll(pollId, user);
    }

    private void requireCreator(User user, long pollId) {
        if (!pollRepository.pollExists(pollId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Poll not found.");
        }
        Long creatorId = pollRepository.findCreatorId(pollId);
        if (creatorId == null || creatorId.longValue() != user.getId().longValue()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the creator of a poll can do this.");
        }
    }

    public Map<String, Integer> getStats() {
        return Map.of("totalPolls", pollRepository.countPolls(), "totalVotes", pollRepository.countVotes());
    }

    // Turns React's ISO time ("2026-10-20T10:00:00.000Z") into MySQL text in UTC ("2026-10-20 10:00:00").
    // Returns null when no expiry was given.
    private String parseExpiry(String expiresAt) {
        if (expiresAt == null || expiresAt.trim().isEmpty()) {
            return null;
        }
        Instant instant;
        try {
            instant = Instant.parse(expiresAt.trim());
        } catch (DateTimeException e) {
            throw badRequest("The closing time is not a valid date and time.");
        }
        if (!instant.isAfter(Instant.now())) {
            throw badRequest("The closing time must be in the future.");
        }
        LocalDateTime utc = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        return utc.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    // ---------- validation ----------
    private void validateNewPoll(CreatePollRequest request) {
        if (request == null) {
            throw badRequest("Request body is missing.");
        }

        String question = request.getQuestion() == null ? "" : request.getQuestion().trim();
        if (question.isEmpty()) {
            throw badRequest("Poll question is required.");
        }
        if (question.length() > 255) {
            throw badRequest("Poll question must be 255 characters or fewer.");
        }

        if (request.getCategory() == null || !CATEGORIES.contains(request.getCategory())) {
            throw badRequest("Please choose a valid category.");
        }

        List<String> options = request.getOptions();
        if (options == null || options.size() < 2 || options.size() > 4) {
            throw badRequest("A poll needs between 2 and 4 options.");
        }

        Set<String> seen = new HashSet<>();
        for (String option : options) {
            String text = option == null ? "" : option.trim();
            if (text.isEmpty()) {
                throw badRequest("Options cannot be empty.");
            }
            if (text.length() > 100) {
                throw badRequest("Each option must be 100 characters or fewer.");
            }
            if (!seen.add(text.toLowerCase())) {
                throw badRequest("Options must be different from each other.");
            }
        }
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
