package com.spamfilter.smsclient.db;

import com.spamfilter.smsclient.model.Subscriber;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** JDBC access to the system-wide subscribers table. */
public class SubscriberRepository {

    private static final String UNIQUE_VIOLATION = "23505";
    private final DataSource dataSource;

    public SubscriberRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private Connection connect() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("Database is not configured");
        }
        return dataSource.getConnection();
    }

    public List<Subscriber> list() {
        String sql = "SELECT id, msisdn, imsi, display_name, status FROM subscribers ORDER BY created_at DESC";
        List<Subscriber> subscribers = new ArrayList<>();
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                subscribers.add(new Subscriber(
                        rs.getString("id"), rs.getString("msisdn"), rs.getString("imsi"),
                        rs.getString("display_name"), rs.getString("status")));
            }
            return subscribers;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load subscribers: " + e.getMessage(), e);
        }
    }

    public void add(String msisdn, String imsi, String displayName, String status) {
        String sql = "INSERT INTO subscribers (msisdn, imsi, display_name, status) VALUES (?, ?, ?, ?)";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, msisdn);
            ps.setString(2, imsi);
            ps.setString(3, displayName);
            ps.setString(4, status);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
                throw new IllegalArgumentException("That MSISDN or IMSI already belongs to a subscriber");
            }
            throw new IllegalStateException("Could not add subscriber: " + e.getMessage(), e);
        }
    }

    public void remove(String id) {
        String sql = "DELETE FROM subscribers WHERE id = ?";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, id, java.sql.Types.OTHER);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not remove subscriber: " + e.getMessage(), e);
        }
    }
}
