package com.pollpulse.service;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.pollpulse.exception.ApiException;
import com.pollpulse.model.AuthResponse;
import com.pollpulse.model.LoginRequest;
import com.pollpulse.model.RegisterRequest;
import com.pollpulse.model.User;
import com.pollpulse.repository.UserRepository;

// Registration, login, logout and "who is this request from?".
@Service
public class AuthService {

    private static final int SESSION_DAYS = 7;
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.]{3,30}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthResponse register(RegisterRequest request) {
        if (request == null) {
            throw badRequest("Request body is missing.");
        }
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        String email = request.getEmail() == null ? "" : request.getEmail().trim();
        String password = request.getPassword() == null ? "" : request.getPassword();

        if (!USERNAME.matcher(username).matches()) {
            throw badRequest("Username must be 3 to 30 characters: letters, numbers, dot or underscore.");
        }
        if (email.length() > 150 || !EMAIL.matcher(email).matches()) {
            throw badRequest("Please enter a valid email address.");
        }
        if (password.length() < 6 || password.length() > 72) {
            throw badRequest("Password must be 6 to 72 characters.");
        }
        if (userRepository.usernameExists(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "That username is already taken. Please choose another.");
        }

        long userId;
        try {
            // NOTE: the same email may be used by many usernames (email is not unique).
            userId = userRepository.insertUser(username, email, passwordEncoder.encode(password));
        } catch (DuplicateKeyException e) {
            // Two people registered the same username at the same moment: MySQL's UNIQUE key stops the second.
            throw new ApiException(HttpStatus.CONFLICT, "That username is already taken. Please choose another.");
        }

        User user = new User();
        user.setId(userId);
        user.setUsername(username);
        user.setEmail(email);
        return new AuthResponse(newSession(userId), user);
    }

    public AuthResponse login(LoginRequest request) {
        if (request == null || request.getUsername() == null || request.getPassword() == null) {
            throw badRequest("Username and password are required.");
        }
        User user = userRepository.findByUsername(request.getUsername().trim()).orElse(null);
        // Same message for "no such user" and "wrong password" so attackers learn nothing.
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Wrong username or password.");
        }
        userRepository.deleteExpiredSessions();
        return new AuthResponse(newSession(user.getId()), user);
    }

    public void logout(String authHeader) {
        String token = extractToken(authHeader);
        if (token != null) {
            userRepository.deleteSession(token);
        }
    }

    // Signed-in user, or throws 401
    public User requireUser(String authHeader) {
        User user = optionalUser(authHeader);
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in first.");
        }
        return user;
    }

    // Signed-in user, or null (used for pages anyone may view)
    public User optionalUser(String authHeader) {
        String token = extractToken(authHeader);
        if (token == null) {
            return null;
        }
        return userRepository.findUserByToken(token).orElse(null);
    }

    // Header looks like:  Authorization: Bearer 3f9a...
    private String extractToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private String newSession(long userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes); // 64 hex characters
        userRepository.createSession(token, userId, SESSION_DAYS);
        return token;
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
