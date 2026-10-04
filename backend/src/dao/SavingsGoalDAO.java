package dao;

import model.SavingsGoal;
import model.SavingsGoalStatus;
import util.DBConnection;

import java.sql.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for 'savings_goals' entity in FinTrack.
 * Handles financial goals, progress tracking, and optional funding account links.
 * All user-facing operations are strictly scoped by user_id.
 */
public class SavingsGoalDAO {

    /**
     * Maps the current row of a ResultSet to a SavingsGoal model object.
     */
    private SavingsGoal mapResultSetToSavingsGoal(ResultSet rs) throws SQLException {
        SavingsGoal goal = new SavingsGoal();
        goal.setGoalId(rs.getInt("goal_id"));
        goal.setUserId(rs.getInt("user_id"));

        int accountId = rs.getInt("account_id");
        if (rs.wasNull()) {
            goal.setAccountId(null);
        } else {
            goal.setAccountId(accountId);
        }

        goal.setGoalName(rs.getString("goal_name"));
        goal.setTargetAmount(rs.getBigDecimal("target_amount"));
        goal.setSavedAmount(rs.getBigDecimal("saved_amount"));

        Date targetDate = rs.getDate("target_date");
        if (targetDate != null) {
            goal.setTargetDate(targetDate.toLocalDate());
        }

        String statusStr = rs.getString("status");
        if (statusStr != null) {
            goal.setStatus(SavingsGoalStatus.valueOf(statusStr));
        }

        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            goal.setCreatedAt(ts.toInstant().atOffset(ZoneOffset.UTC));
        }

        return goal;
    }

    /**
     * Inserts a new savings goal into the database.
     * Supports optional account_id (can be null).
     * Populates generated goal_id.
     *
     * @param goal the SavingsGoal to create
     * @return the created SavingsGoal with generated goal_id populated
     * @throws SQLException if a database error occurs
     */
    public SavingsGoal createSavingsGoal(SavingsGoal goal) throws SQLException {
        String sql = "INSERT INTO savings_goals (user_id, account_id, goal_name, target_amount, saved_amount, target_date, status, created_at) " +
                     "VALUES (?, ?, ?, ?, COALESCE(?, 0.00), ?, ?, COALESCE(?, CURRENT_TIMESTAMP))";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, goal.getUserId());

            if (goal.getAccountId() != null) {
                stmt.setInt(2, goal.getAccountId());
            } else {
                stmt.setNull(2, Types.INTEGER);
            }

            stmt.setString(3, goal.getGoalName());
            stmt.setBigDecimal(4, goal.getTargetAmount());
            stmt.setBigDecimal(5, goal.getSavedAmount());
            stmt.setDate(6, goal.getTargetDate() != null ? Date.valueOf(goal.getTargetDate()) : null);
            stmt.setString(7, goal.getStatus() != null ? goal.getStatus().name() : SavingsGoalStatus.Active.name());

            if (goal.getCreatedAt() != null) {
                stmt.setTimestamp(8, Timestamp.from(goal.getCreatedAt().toInstant()));
            } else {
                stmt.setNull(8, Types.TIMESTAMP);
            }

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    goal.setGoalId(rs.getInt(1));
                }
            }
        }
        return goal;
    }

    /**
     * Retrieves a savings goal by goal_id.
     *
     * @param goalId the goal ID
     * @return SavingsGoal if found, null otherwise
     * @throws SQLException if a database error occurs
     */
    public SavingsGoal findById(int goalId) throws SQLException {
        String sql = "SELECT goal_id, user_id, account_id, goal_name, target_amount, saved_amount, target_date, status, created_at " +
                     "FROM savings_goals WHERE goal_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, goalId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToSavingsGoal(rs);
                }
            }
        }
        return null;
    }

    /**
     * User-scoped lookup: retrieves a savings goal ensuring it belongs to the requesting user.
     *
     * @param userId the owner user ID
     * @param goalId the goal ID
     * @return SavingsGoal if found and owned by user, null otherwise
     * @throws SQLException if a database error occurs
     */
    public SavingsGoal findById(int userId, int goalId) throws SQLException {
        String sql = "SELECT goal_id, user_id, account_id, goal_name, target_amount, saved_amount, target_date, status, created_at " +
                     "FROM savings_goals WHERE goal_id = ? AND user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, goalId);
            stmt.setInt(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToSavingsGoal(rs);
                }
            }
        }
        return null;
    }

    /**
     * Retrieves all savings goals belonging to a specific user.
     *
     * @param userId the user ID
     * @return List of savings goals for the user
     * @throws SQLException if a database error occurs
     */
    public List<SavingsGoal> getGoalsByUserId(int userId) throws SQLException {
        String sql = "SELECT goal_id, user_id, account_id, goal_name, target_amount, saved_amount, target_date, status, created_at " +
                     "FROM savings_goals WHERE user_id = ? ORDER BY target_date ASC, goal_id ASC";
        List<SavingsGoal> goals = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    goals.add(mapResultSetToSavingsGoal(rs));
                }
            }
        }
        return goals;
    }

    /**
     * Updates an existing savings goal.
     * Enforces user scoping in the WHERE clause: WHERE goal_id = ? AND user_id = ?.
     *
     * @param goal the SavingsGoal containing updated fields
     * @return true if updated, false otherwise
     * @throws SQLException if a database error occurs
     */
    public boolean updateSavingsGoal(SavingsGoal goal) throws SQLException {
        String sql = "UPDATE savings_goals SET account_id = ?, goal_name = ?, target_amount = ?, saved_amount = ?, " +
                     "target_date = ?, status = ? " +
                     "WHERE goal_id = ? AND user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (goal.getAccountId() != null) {
                stmt.setInt(1, goal.getAccountId());
            } else {
                stmt.setNull(1, Types.INTEGER);
            }

            stmt.setString(2, goal.getGoalName());
            stmt.setBigDecimal(3, goal.getTargetAmount());
            stmt.setBigDecimal(4, goal.getSavedAmount());
            stmt.setDate(5, goal.getTargetDate() != null ? Date.valueOf(goal.getTargetDate()) : null);
            stmt.setString(6, goal.getStatus() != null ? goal.getStatus().name() : SavingsGoalStatus.Active.name());
            stmt.setInt(7, goal.getGoalId());
            stmt.setInt(8, goal.getUserId());

            int rowsUpdated = stmt.executeUpdate();
            return rowsUpdated > 0;
        }
    }

    /**
     * Deletes a savings goal, strictly enforcing user ownership.
     *
     * @param userId the owner user ID
     * @param goalId the goal ID to delete
     * @return true if deleted, false otherwise
     * @throws SQLException if a database error occurs
     */
    public boolean deleteSavingsGoal(int userId, int goalId) throws SQLException {
        String sql = "DELETE FROM savings_goals WHERE goal_id = ? AND user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, goalId);
            stmt.setInt(2, userId);

            int rowsDeleted = stmt.executeUpdate();
            return rowsDeleted > 0;
        }
    }
}
