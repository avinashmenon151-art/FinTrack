package dao;

import model.Budget;
import util.DBConnection;

import java.sql.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for 'budgets' entity in FinTrack.
 * Enforces user scoping across all budget operations.
 */
public class BudgetDAO {

    /**
     * Maps the current row of a ResultSet to a Budget model object.
     */
    private Budget mapResultSetToBudget(ResultSet rs) throws SQLException {
        Budget budget = new Budget();
        budget.setBudgetId(rs.getInt("budget_id"));
        budget.setUserId(rs.getInt("user_id"));
        budget.setBudgetName(rs.getString("budget_name"));

        Date startDate = rs.getDate("start_date");
        if (startDate != null) {
            budget.setStartDate(startDate.toLocalDate());
        }

        Date endDate = rs.getDate("end_date");
        if (endDate != null) {
            budget.setEndDate(endDate.toLocalDate());
        }

        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            budget.setCreatedAt(ts.toInstant().atOffset(ZoneOffset.UTC));
        }

        return budget;
    }

    /**
     * Creates a new budget.
     * Populates generated budget_id.
     *
     * @param budget the Budget to create
     * @return the created Budget with generated budget_id
     * @throws SQLException if a database error occurs
     */
    public Budget createBudget(Budget budget) throws SQLException {
        String sql = "INSERT INTO budgets (user_id, budget_name, start_date, end_date, created_at) " +
                     "VALUES (?, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP))";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, budget.getUserId());
            stmt.setString(2, budget.getBudgetName());
            stmt.setDate(3, budget.getStartDate() != null ? Date.valueOf(budget.getStartDate()) : null);
            stmt.setDate(4, budget.getEndDate() != null ? Date.valueOf(budget.getEndDate()) : null);

            if (budget.getCreatedAt() != null) {
                stmt.setTimestamp(5, Timestamp.from(budget.getCreatedAt().toInstant()));
            } else {
                stmt.setNull(5, Types.TIMESTAMP);
            }

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    budget.setBudgetId(rs.getInt(1));
                }
            }
        }
        return budget;
    }

    /**
     * Retrieves a budget by budget_id.
     *
     * @param budgetId the budget ID
     * @return Budget if found, null otherwise
     * @throws SQLException if a database error occurs
     */
    public Budget findById(int budgetId) throws SQLException {
        String sql = "SELECT budget_id, user_id, budget_name, start_date, end_date, created_at " +
                     "FROM budgets WHERE budget_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, budgetId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToBudget(rs);
                }
            }
        }
        return null;
    }

    /**
     * User-scoped lookup: retrieves a budget ensuring it belongs to the given user.
     *
     * @param userId the owner user ID
     * @param budgetId the budget ID
     * @return Budget if found and owned by user, null otherwise
     * @throws SQLException if a database error occurs
     */
    public Budget findById(int userId, int budgetId) throws SQLException {
        String sql = "SELECT budget_id, user_id, budget_name, start_date, end_date, created_at " +
                     "FROM budgets WHERE budget_id = ? AND user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, budgetId);
            stmt.setInt(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToBudget(rs);
                }
            }
        }
        return null;
    }

    /**
     * Retrieves all budgets established by a user.
     *
     * @param userId the user ID
     * @return List of budgets belonging to the user
     * @throws SQLException if a database error occurs
     */
    public List<Budget> getBudgetsByUserId(int userId) throws SQLException {
        String sql = "SELECT budget_id, user_id, budget_name, start_date, end_date, created_at " +
                     "FROM budgets WHERE user_id = ? ORDER BY start_date DESC, budget_id DESC";
        List<Budget> budgets = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    budgets.add(mapResultSetToBudget(rs));
                }
            }
        }
        return budgets;
    }

    /**
     * Updates an existing budget.
     * Enforces user scoping in WHERE clause.
     *
     * @param budget the Budget containing updated fields
     * @return true if updated, false otherwise
     * @throws SQLException if a database error occurs
     */
    public boolean updateBudget(Budget budget) throws SQLException {
        String sql = "UPDATE budgets SET budget_name = ?, start_date = ?, end_date = ? " +
                     "WHERE budget_id = ? AND user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, budget.getBudgetName());
            stmt.setDate(2, budget.getStartDate() != null ? Date.valueOf(budget.getStartDate()) : null);
            stmt.setDate(3, budget.getEndDate() != null ? Date.valueOf(budget.getEndDate()) : null);
            stmt.setInt(4, budget.getBudgetId());
            stmt.setInt(5, budget.getUserId());

            int rowsUpdated = stmt.executeUpdate();
            return rowsUpdated > 0;
        }
    }

    /**
     * Deletes a budget, strictly enforcing user ownership.
     *
     * @param userId the requesting user ID
     * @param budgetId the budget ID to delete
     * @return true if deleted, false otherwise
     * @throws SQLException if a database error occurs
     */
    public boolean deleteBudget(int userId, int budgetId) throws SQLException {
        String sql = "DELETE FROM budgets WHERE budget_id = ? AND user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, budgetId);
            stmt.setInt(2, userId);

            int rowsDeleted = stmt.executeUpdate();
            return rowsDeleted > 0;
        }
    }
}
