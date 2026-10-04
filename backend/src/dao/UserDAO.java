package dao;

import model.User;
import util.DBConnection;

import java.sql.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for 'users' entity in FinTrack.
 * Handles user persistence, retrieval by ID or email, and updates.
 */
public class UserDAO {

    /**
     * Maps the current row of a ResultSet to a User model object.
     */
    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getInt("user_id"));
        user.setName(rs.getString("name"));
        user.setEmail(rs.getString("email"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setPhoneNumber(rs.getString("phone_number"));

        int defaultAccountId = rs.getInt("default_account_id");
        if (rs.wasNull()) {
            user.setDefaultAccountId(null);
        } else {
            user.setDefaultAccountId(defaultAccountId);
        }

        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            user.setCreatedAt(ts.toInstant().atOffset(ZoneOffset.UTC));
        }

        return user;
    }

    /**
     * Inserts a new user into the database.
     * Allows default_account_id to initially be NULL.
     * Sets the generated user_id on the user object.
     *
     * @param user the User to create
     * @return the created User with generated user_id populated
     * @throws SQLException if a database error occurs
     */
    public User createUser(User user) throws SQLException {
        String sql = "INSERT INTO users (name, email, password_hash, phone_number, default_account_id, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP))";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, user.getName());
            stmt.setString(2, user.getEmail());
            stmt.setString(3, user.getPasswordHash());
            stmt.setString(4, user.getPhoneNumber());

            if (user.getDefaultAccountId() != null) {
                stmt.setInt(5, user.getDefaultAccountId());
            } else {
                stmt.setNull(5, Types.INTEGER);
            }

            if (user.getCreatedAt() != null) {
                stmt.setTimestamp(6, Timestamp.from(user.getCreatedAt().toInstant()));
            } else {
                stmt.setNull(6, Types.TIMESTAMP);
            }

            stmt.executeUpdate();

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    user.setUserId(generatedKeys.getInt(1));
                }
            }
        }
        return user;
    }

    /**
     * Retrieves a user by their unique primary key (user_id).
     *
     * @param userId the user ID to search for
     * @return User if found, null otherwise
     * @throws SQLException if a database error occurs
     */
    public User findById(int userId) throws SQLException {
        String sql = "SELECT user_id, name, email, password_hash, phone_number, default_account_id, created_at " +
                     "FROM users WHERE user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToUser(rs);
                }
            }
        }
        return null;
    }

    /**
     * Retrieves a user by their unique email address.
     *
     * @param email the email address to search for
     * @return User if found, null otherwise
     * @throws SQLException if a database error occurs
     */
    public User findByEmail(String email) throws SQLException {
        String sql = "SELECT user_id, name, email, password_hash, phone_number, default_account_id, created_at " +
                     "FROM users WHERE email = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToUser(rs);
                }
            }
        }
        return null;
    }

    /**
     * Retrieves all registered users in the system.
     *
     * @return List of all User objects
     * @throws SQLException if a database error occurs
     */
    public List<User> getAllUsers() throws SQLException {
        String sql = "SELECT user_id, name, email, password_hash, phone_number, default_account_id, created_at " +
                     "FROM users ORDER BY user_id ASC";
        List<User> users = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                users.add(mapResultSetToUser(rs));
            }
        }
        return users;
    }

    /**
     * Updates an existing user's profile information.
     *
     * @param user the User containing updated fields
     * @return true if the record was updated, false otherwise
     * @throws SQLException if a database error occurs
     */
    public boolean updateUser(User user) throws SQLException {
        String sql = "UPDATE users SET name = ?, email = ?, password_hash = ?, phone_number = ?, default_account_id = ? " +
                     "WHERE user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, user.getName());
            stmt.setString(2, user.getEmail());
            stmt.setString(3, user.getPasswordHash());
            stmt.setString(4, user.getPhoneNumber());

            if (user.getDefaultAccountId() != null) {
                stmt.setInt(5, user.getDefaultAccountId());
            } else {
                stmt.setNull(5, Types.INTEGER);
            }

            stmt.setInt(6, user.getUserId());

            int rowsUpdated = stmt.executeUpdate();
            return rowsUpdated > 0;
        }
    }

    /**
     * Deletes a user by user_id. Cascades to user-owned accounts, transactions, etc.
     *
     * @param userId the ID of the user to delete
     * @return true if deleted, false otherwise
     * @throws SQLException if a database error occurs
     */
    public boolean deleteUser(int userId) throws SQLException {
        String sql = "DELETE FROM users WHERE user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            int rowsDeleted = stmt.executeUpdate();
            return rowsDeleted > 0;
        }
    }
}
