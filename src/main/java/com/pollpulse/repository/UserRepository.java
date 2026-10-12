package com.pollpulse.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.pollpulse.model.User;

// Talks to the users and sessions tables.
@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<User> userMapper = (rs, rowNum) -> {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setEmail(rs.getString("email"));
        user.setPasswordHash(rs.getString("password_hash"));
        return user;
    };

    // Throws DuplicateKeyException if the username already exists (UNIQUE key in MySQL)
    public long insertUser(String username, String email, String passwordHash) {
        String sql = "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, username);
            ps.setString(2, email);
            ps.setString(3, passwordHash);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public boolean usernameExists(String username) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, username);
        return count != null && count > 0;
    }

    public Optional<User> findByUsername(String username) {
        return jdbcTemplate.query(
                "SELECT id, username, email, password_hash FROM users WHERE username = ?",
                userMapper, username).stream().findFirst();
    }

    public void createSession(String token, long userId, int days) {
        jdbcTemplate.update(
                "INSERT INTO sessions (token, user_id, expires_at) VALUES (?, ?, DATE_ADD(UTC_TIMESTAMP(), INTERVAL ? DAY))",
                token, userId, days);
    }

    // The user who owns this token, if the session exists and has not expired
    public Optional<User> findUserByToken(String token) {
        return jdbcTemplate.query(
                "SELECT u.id, u.username, u.email, u.password_hash FROM sessions s "
                        + "JOIN users u ON u.id = s.user_id "
                        + "WHERE s.token = ? AND s.expires_at > UTC_TIMESTAMP()",
                userMapper, token).stream().findFirst();
    }

    public void deleteSession(String token) {
        jdbcTemplate.update("DELETE FROM sessions WHERE token = ?", token);
    }

    public void deleteExpiredSessions() {
        jdbcTemplate.update("DELETE FROM sessions WHERE expires_at <= UTC_TIMESTAMP()");
    }
}
