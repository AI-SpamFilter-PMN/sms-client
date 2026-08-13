package com.spamfilter.smsclient.db;

import com.spamfilter.smsclient.model.MessageRecord;
import com.spamfilter.smsclient.model.PhoneNumber;
import com.spamfilter.smsclient.model.User;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC access to the users/phone_numbers tables (this app's own accounts -
 * additive, decoupled from the SMPP server's schema).
 */
public class UserRepository {

    private static final String UNIQUE_VIOLATION = "23505";

    private final DataSource dataSource;

    public UserRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private Connection connect() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("Database is not configured");
        }
        return dataSource.getConnection();
    }

    public User register(String email, String password, String displayName) {
        String sql = "INSERT INTO users (email, password, display_name) VALUES (?, ?, ?) "
                + "RETURNING id, email, display_name, role";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, password);
            ps.setString(3, displayName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new User(rs.getString("id"), rs.getString("email"), rs.getString("display_name"),
                        rs.getString("role"));
            }
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
                throw new IllegalArgumentException("An account with that email already exists");
            }
            throw new IllegalStateException("Could not create account: " + e.getMessage(), e);
        }
    }

    public User authenticate(String email, String password) {
        String sql = "SELECT id, email, display_name, password, role FROM users WHERE email = ?";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                if (!password.equals(rs.getString("password"))) {
                    return null;
                }
                return new User(rs.getString("id"), rs.getString("email"), rs.getString("display_name"),
                        rs.getString("role"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not verify account: " + e.getMessage(), e);
        }
    }

    public List<PhoneNumber> listNumbers(String userId) {
        String sql = "SELECT id, msisdn, label FROM phone_numbers WHERE user_id = ? ORDER BY created_at";
        List<PhoneNumber> numbers = new ArrayList<>();
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, userId, java.sql.Types.OTHER);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    numbers.add(new PhoneNumber(rs.getString("id"), rs.getString("msisdn"), rs.getString("label")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load numbers: " + e.getMessage(), e);
        }
        return numbers;
    }

    public void addNumber(String userId, String msisdn, String label) {
        String sql = "INSERT INTO phone_numbers (user_id, msisdn, label) VALUES (?, ?, ?)";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, userId, java.sql.Types.OTHER);
            ps.setString(2, msisdn);
            ps.setString(3, label);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
                throw new IllegalArgumentException("That number is already registered");
            }
            throw new IllegalStateException("Could not add number: " + e.getMessage(), e);
        }
    }

    public void removeNumber(String userId, String numberId) {
        String sql = "DELETE FROM phone_numbers WHERE id = ? AND user_id = ?";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, numberId, java.sql.Types.OTHER);
            ps.setObject(2, userId, java.sql.Types.OTHER);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not remove number: " + e.getMessage(), e);
        }
    }

    public boolean userOwnsNumber(String userId, String msisdn) {
        String sql = "SELECT 1 FROM phone_numbers WHERE user_id = ? AND msisdn = ?";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, userId, java.sql.Types.OTHER);
            ps.setString(2, msisdn);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not verify number ownership: " + e.getMessage(), e);
        }
    }

    public List<MessageRecord> historyForUser(String userId, int limit) {
        String sql = "SELECT id, source, destination, classification_label, classification_score, "
                + "status, smpp_message_id, received_at FROM messages "
                + "WHERE source IN (SELECT msisdn FROM phone_numbers WHERE user_id = ?) "
                + "   OR destination IN (SELECT msisdn FROM phone_numbers WHERE user_id = ?) "
                + "ORDER BY received_at DESC LIMIT ?";
        List<MessageRecord> records = new ArrayList<>();
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, userId, java.sql.Types.OTHER);
            ps.setObject(2, userId, java.sql.Types.OTHER);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(new MessageRecord(
                            rs.getString("id"),
                            rs.getString("source"),
                            rs.getString("destination"),
                            rs.getString("classification_label"),
                            rs.getDouble("classification_score"),
                            rs.getString("status"),
                            rs.getString("smpp_message_id"),
                            rs.getTimestamp("received_at").toInstant()));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load message history: " + e.getMessage(), e);
        }
        return records;
    }
}
