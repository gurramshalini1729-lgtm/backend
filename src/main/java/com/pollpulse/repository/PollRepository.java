package com.pollpulse.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.pollpulse.model.Poll;
import com.pollpulse.model.PollOption;

// Talks to polls, poll_options and votes. Every "?" is filled safely by JdbcTemplate
// (parameterized query), which prevents SQL injection.
@Repository
public class PollRepository {

    private final JdbcTemplate jdbcTemplate;

    public PollRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // "closed" is decided by MySQL's clock (UTC), so the server and database never disagree.
    private static final String POLL_SELECT =
            "SELECT p.id, p.question, p.category, p.created_at, p.expires_at, "
            + "(p.expires_at IS NOT NULL AND p.expires_at <= UTC_TIMESTAMP()) AS closed, "
            + "u.username AS creator, COUNT(v.id) AS total_votes "
            + "FROM polls p "
            + "LEFT JOIN users u ON u.id = p.creator_id "
            + "LEFT JOIN votes v ON v.poll_id = p.id ";

    private static final String POLL_GROUP_BY =
            "GROUP BY p.id, p.question, p.category, p.created_at, p.expires_at, u.username ";

    // Converts one database row into a Poll object
    private final RowMapper<Poll> pollMapper = (rs, rowNum) -> {
        Poll poll = new Poll();
        poll.setId(rs.getLong("id"));
        poll.setQuestion(rs.getString("question"));
        poll.setCategory(rs.getString("category"));
        poll.setCreator(rs.getString("creator"));
        poll.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        poll.setTotalVotes(rs.getInt("total_votes"));
        poll.setClosed(rs.getBoolean("closed"));

        // expires_at is stored as UTC text like "2026-10-20 10:00:00" -> "2026-10-20T10:00:00Z"
        String expires = rs.getString("expires_at");
        if (expires != null) {
            poll.setExpiresAt(expires.substring(0, 19).replace(' ', 'T') + "Z");
        }
        return poll;
    };

    private final RowMapper<PollOption> optionMapper = (rs, rowNum) -> {
        PollOption option = new PollOption();
        option.setId(rs.getLong("id"));
        option.setOptionText(rs.getString("option_text"));
        option.setVoteCount(rs.getInt("vote_count"));
        return option;
    };

    // All polls (newest first) with their total vote count.
    public List<Poll> findAllPolls() {
        String sql = POLL_SELECT + POLL_GROUP_BY + "ORDER BY p.created_at DESC, p.id DESC";
        return jdbcTemplate.query(sql, pollMapper);
    }

    public Optional<Poll> findPollById(long pollId) {
        String sql = POLL_SELECT + "WHERE p.id = ? " + POLL_GROUP_BY;
        return jdbcTemplate.query(sql, pollMapper, pollId).stream().findFirst();
    }

    public List<PollOption> findOptionsWithVoteCounts(long pollId) {
        String sql = "SELECT o.id, o.option_text, COUNT(v.id) AS vote_count "
                + "FROM poll_options o LEFT JOIN votes v ON v.option_id = o.id "
                + "WHERE o.poll_id = ? "
                + "GROUP BY o.id, o.option_text "
                + "ORDER BY o.id";
        return jdbcTemplate.query(sql, optionMapper, pollId);
    }

    // Polls created by this user (newest first)
    public List<Poll> findPollsByCreator(long userId) {
        String sql = POLL_SELECT + "WHERE p.creator_id = ? " + POLL_GROUP_BY + "ORDER BY p.created_at DESC, p.id DESC";
        return jdbcTemplate.query(sql, pollMapper, userId);
    }

    // Polls this user has voted in (newest first)
    public List<Poll> findPollsVotedBy(long userId) {
        String sql = POLL_SELECT
                + "WHERE p.id IN (SELECT poll_id FROM votes WHERE user_id = ?) "
                + POLL_GROUP_BY + "ORDER BY p.created_at DESC, p.id DESC";
        return jdbcTemplate.query(sql, pollMapper, userId);
    }

    // id of the user who created the poll (null for old sample polls that have no creator)
    public Long findCreatorId(long pollId) {
        List<Long> result = jdbcTemplate.queryForList(
                "SELECT creator_id FROM polls WHERE id = ? AND creator_id IS NOT NULL", Long.class, pollId);
        return result.isEmpty() ? null : result.get(0);
    }

    // Deletes the poll; MySQL (ON DELETE CASCADE) also deletes its options and votes
    public void deletePoll(long pollId) {
        jdbcTemplate.update("DELETE FROM polls WHERE id = ?", pollId);
    }

    // Closes the poll right now. Returns 1 if it was open, 0 if it was already closed.
    public int closePollNow(long pollId) {
        return jdbcTemplate.update(
                "UPDATE polls SET expires_at = UTC_TIMESTAMP() "
                + "WHERE id = ? AND (expires_at IS NULL OR expires_at > UTC_TIMESTAMP())", pollId);
    }

    // The option this user voted for in this poll, or null if they have not voted
    public Long findVotedOption(long pollId, long userId) {
        List<Long> result = jdbcTemplate.queryForList(
                "SELECT option_id FROM votes WHERE poll_id = ? AND user_id = ?", Long.class, pollId, userId);
        return result.isEmpty() ? null : result.get(0);
    }

    // expiresAtUtc: "yyyy-MM-dd HH:mm:ss" in UTC, or null for a poll that never closes
    public long insertPoll(String question, String category, long creatorId, String expiresAtUtc) {
        String sql = "INSERT INTO polls (question, category, creator_id, expires_at) VALUES (?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, question);
            ps.setString(2, category);
            ps.setLong(3, creatorId);
            ps.setString(4, expiresAtUtc);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void insertOption(long pollId, String optionText) {
        jdbcTemplate.update("INSERT INTO poll_options (poll_id, option_text) VALUES (?, ?)", pollId, optionText);
    }

    // Inserts the vote ONLY IF the poll is still open. Returns 1 (saved) or 0 (poll is closed).
    // Throws DuplicateKeyException if this user already voted (UNIQUE poll_id + user_id in MySQL).
    public int insertVoteIfOpen(long pollId, long optionId, long userId) {
        String sql = "INSERT INTO votes (poll_id, option_id, user_id) "
                + "SELECT p.id, ?, ? FROM polls p "
                + "WHERE p.id = ? AND (p.expires_at IS NULL OR p.expires_at > UTC_TIMESTAMP())";
        return jdbcTemplate.update(sql, optionId, userId, pollId);
    }

    public boolean pollExists(long pollId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM polls WHERE id = ?", Integer.class, pollId);
        return count != null && count > 0;
    }

    public boolean optionBelongsToPoll(long pollId, long optionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM poll_options WHERE id = ? AND poll_id = ?", Integer.class, optionId, pollId);
        return count != null && count > 0;
    }

    public int countPolls() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM polls", Integer.class);
        return count == null ? 0 : count;
    }

    public int countVotes() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM votes", Integer.class);
        return count == null ? 0 : count;
    }
}
