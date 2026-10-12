package com.pollpulse.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pollpulse.model.CreatePollRequest;
import com.pollpulse.model.Poll;
import com.pollpulse.model.User;
import com.pollpulse.model.VoteRequest;
import com.pollpulse.service.AuthService;
import com.pollpulse.service.PollService;

// REST endpoints for polls. Viewing is public; creating and voting need a signed-in user.
@RestController
@RequestMapping("/api")
public class PollController {

    private final PollService pollService;
    private final AuthService authService;

    public PollController(PollService pollService, AuthService authService) {
        this.pollService = pollService;
        this.authService = authService;
    }

    // GET /api/polls  -> list all polls (public)
    @GetMapping("/polls")
    public List<Poll> getAllPolls() {
        return pollService.getAllPolls();
    }

    // GET /api/polls/5  -> one poll with options (public; includes yourOptionId if you are signed in)
    @GetMapping("/polls/{id}")
    public Poll getPoll(@PathVariable long id,
                        @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return pollService.getPoll(id, authService.optionalUser(authHeader));
    }

    // POST /api/polls  -> create a poll (sign-in required)
    @PostMapping("/polls")
    public ResponseEntity<Poll> createPoll(@RequestBody CreatePollRequest request,
                                           @RequestHeader(value = "Authorization", required = false) String authHeader) {
        User user = authService.requireUser(authHeader);
        Poll createdPoll = pollService.createPoll(user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPoll);
    }

    // POST /api/polls/5/vote  -> save a vote (sign-in required, one vote per user per poll)
    @PostMapping("/polls/{id}/vote")
    public ResponseEntity<Map<String, Object>> vote(@PathVariable long id, @RequestBody VoteRequest request,
                                                    @RequestHeader(value = "Authorization", required = false) String authHeader) {
        User user = authService.requireUser(authHeader);
        Poll results = pollService.vote(user, id, request);
        Map<String, Object> body = Map.of("message", "Thank you! Your vote has been recorded.", "results", results);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    // GET /api/polls/5/results  -> vote counts and percentages (public)
    @GetMapping("/polls/{id}/results")
    public Poll getResults(@PathVariable long id,
                           @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return pollService.getPoll(id, authService.optionalUser(authHeader));
    }

    // GET /api/me/polls  -> { "created": [...], "voted": [...] }  (sign-in required)
    @GetMapping("/me/polls")
    public Map<String, List<Poll>> getMyPolls(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        return pollService.getMyPolls(authService.requireUser(authHeader));
    }

    // POST /api/polls/5/close  -> creator closes the poll now
    @PostMapping("/polls/{id}/close")
    public Poll closePoll(@PathVariable long id,
                          @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return pollService.closePoll(authService.requireUser(authHeader), id);
    }

    // DELETE /api/polls/5  -> creator deletes the poll
    @DeleteMapping("/polls/{id}")
    public ResponseEntity<Void> deletePoll(@PathVariable long id,
                                           @RequestHeader(value = "Authorization", required = false) String authHeader) {
        pollService.deletePoll(authService.requireUser(authHeader), id);
        return ResponseEntity.noContent().build();
    }

    // GET /api/stats  -> { "totalPolls": 5, "totalVotes": 30 }
    @GetMapping("/stats")
    public Map<String, Integer> getStats() {
        return pollService.getStats();
    }
}
