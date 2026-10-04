package dao;

import model.BudgetCategory;
import util.DBConnection;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for 'budget_category' associative entity in FinTrack.
 *
 * Handles composite primary key (budget_id, category_id) mappings and category-specific
 * allocated amounts (> 0).
 */
public class BudgetCategoryDAO {

    /**
     * Maps the current row of a ResultSet to a BudgetCategory model object.
     */
    private BudgetCategory mapResultSetToBudgetCategory(ResultSet rs) throws SQLException {
        BudgetCategory bc = new BudgetCategory();
        bc.setBudgetId(rs.getInt("budget_id"));
        bc.setCategoryId(rs.getInt("category_id"));
        bc.setAllocatedAmount(rs.getBigDecimal("allocated_amount"));
        return bc;
    }

    /**
     * Adds a category allocation to a budget.
     * Note: The primary key (budget_id, category_id) is composite and caller-provided;
     * no generated keys are requested.
     *
     * @param budgetCategory the BudgetCategory to insert
     * @return true if inserted, false otherwise
     * @throws SQLException if a database error occurs or constraint is violated
     */
    public boolean addCategoryToBudget(BudgetCategory budgetCategory) throws SQLException {
        String sql = "INSERT INTO budget_category (budget_id, category_id, allocated_amount) VALUES (?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, budgetCategory.getBudgetId());
            stmt.setInt(2, budgetCategory.getCategoryId());
            stmt.setBigDecimal(3, budgetCategory.getAllocatedAmount());

            int rowsInserted = stmt.executeUpdate();
            return rowsInserted > 0;
        }
    }

    /**
     * Removes a category mapping from a budget.
     *
     * @param budgetId the budget ID
     * @param categoryId the category ID
     * @return true if deleted, false otherwise
     * @throws SQLException if a database error occurs
     */
    public boolean removeCategoryFromBudget(int budgetId, int categoryId) throws SQLException {
        String sql = "DELETE FROM budget_category WHERE budget_id = ? AND category_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, budgetId);
            stmt.setInt(2, categoryId);

            int rowsDeleted = stmt.executeUpdate();
            return rowsDeleted > 0;
        }
    }

    /**
     * Retrieves all category allocations mapped to a specific budget.
     *
     * @param budgetId the budget ID
     * @return List of BudgetCategory mappings for the budget
     * @throws SQLException if a database error occurs
     */
    public List<BudgetCategory> getCategoriesForBudget(int budgetId) throws SQLException {
        String sql = "SELECT budget_id, category_id, allocated_amount FROM budget_category " +
                     "WHERE budget_id = ? ORDER BY category_id ASC";
        List<BudgetCategory> list = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, budgetId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToBudgetCategory(rs));
                }
            }
        }
        return list;
    }

    /**
     * Retrieves all budget allocations that include a specific category.
     *
     * @param categoryId the category ID
     * @return List of BudgetCategory mappings referencing the category
     * @throws SQLException if a database error occurs
     */
    public List<BudgetCategory> getBudgetsForCategory(int categoryId) throws SQLException {
        String sql = "SELECT budget_id, category_id, allocated_amount FROM budget_category " +
                     "WHERE category_id = ? ORDER BY budget_id ASC";
        List<BudgetCategory> list = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, categoryId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToBudgetCategory(rs));
                }
            }
        }
        return list;
    }

    /**
     * Updates the allocated amount for a specific budget and category combination.
     *
     * @param budgetId the budget ID
     * @param categoryId the category ID
     * @param allocatedAmount the new allocated amount (must be > 0)
     * @return true if updated, false otherwise
     * @throws SQLException if a database error occurs
     */
    public boolean updateAllocatedAmount(int budgetId, int categoryId, BigDecimal allocatedAmount) throws SQLException {
        String sql = "UPDATE budget_category SET allocated_amount = ? WHERE budget_id = ? AND category_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setBigDecimal(1, allocatedAmount);
            stmt.setInt(2, budgetId);
            stmt.setInt(3, categoryId);

            int rowsUpdated = stmt.executeUpdate();
            return rowsUpdated > 0;
        }
    }
}
