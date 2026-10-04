package dao;

import model.Category;
import model.CategoryType;
import util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for 'categories' entity in FinTrack.
 * Handles system categories (user_id IS NULL) and user-owned custom categories.
 */
public class CategoryDAO {

    /**
     * Maps the current row of a ResultSet to a Category model object.
     */
    private Category mapResultSetToCategory(ResultSet rs) throws SQLException {
        Category category = new Category();
        category.setCategoryId(rs.getInt("category_id"));

        int userId = rs.getInt("user_id");
        if (rs.wasNull()) {
            category.setUserId(null);
        } else {
            category.setUserId(userId);
        }

        category.setCategoryName(rs.getString("category_name"));

        String typeStr = rs.getString("category_type");
        if (typeStr != null) {
            category.setCategoryType(CategoryType.valueOf(typeStr));
        }

        category.setDescription(rs.getString("description"));
        return category;
    }

    /**
     * Inserts a new category into the database.
     * Supports both system categories (user_id == null) and custom categories (user_id != null).
     * Populates generated category_id.
     *
     * @param category the Category to create
     * @return the created Category with generated category_id
     * @throws SQLException if a database error occurs
     */
    public Category createCategory(Category category) throws SQLException {
        String sql = "INSERT INTO categories (user_id, category_name, category_type, description) " +
                     "VALUES (?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            if (category.getUserId() != null) {
                stmt.setInt(1, category.getUserId());
            } else {
                stmt.setNull(1, Types.INTEGER);
            }

            stmt.setString(2, category.getCategoryName());
            stmt.setString(3, category.getCategoryType() != null ? category.getCategoryType().name() : null);
            stmt.setString(4, category.getDescription());

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    category.setCategoryId(rs.getInt(1));
                }
            }
        }
        return category;
    }

    /**
     * Retrieves a category by its primary key (category_id).
     *
     * @param categoryId the category ID to search for
     * @return Category if found, null otherwise
     * @throws SQLException if a database error occurs
     */
    public Category findById(int categoryId) throws SQLException {
        String sql = "SELECT category_id, user_id, category_name, category_type, description " +
                     "FROM categories WHERE category_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, categoryId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToCategory(rs);
                }
            }
        }
        return null;
    }

    /**
     * Retrieves all system categories (where user_id IS NULL).
     *
     * @return List of system-wide shared categories
     * @throws SQLException if a database error occurs
     */
    public List<Category> getSystemCategories() throws SQLException {
        String sql = "SELECT category_id, user_id, category_name, category_type, description " +
                     "FROM categories WHERE user_id IS NULL ORDER BY category_name ASC";
        List<Category> categories = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                categories.add(mapResultSetToCategory(rs));
            }
        }
        return categories;
    }

    /**
     * Retrieves all custom categories created and owned by a specific user.
     *
     * @param userId the user ID
     * @return List of custom categories for the user
     * @throws SQLException if a database error occurs
     */
    public List<Category> getCategoriesByUserId(int userId) throws SQLException {
        String sql = "SELECT category_id, user_id, category_name, category_type, description " +
                     "FROM categories WHERE user_id = ? ORDER BY category_name ASC";
        List<Category> categories = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    categories.add(mapResultSetToCategory(rs));
                }
            }
        }
        return categories;
    }

    /**
     * Retrieves all categories available for a specific user to use.
     * Includes all shared system categories (user_id IS NULL) plus the user's custom categories.
     * Strictly avoids exposing custom categories created by other users.
     *
     * @param userId the user ID
     * @return List of available categories for the user
     * @throws SQLException if a database error occurs
     */
    public List<Category> getAvailableCategoriesForUser(int userId) throws SQLException {
        String sql = "SELECT category_id, user_id, category_name, category_type, description " +
                     "FROM categories WHERE user_id IS NULL OR user_id = ? " +
                     "ORDER BY CASE WHEN user_id IS NULL THEN 0 ELSE 1 END, category_name ASC";
        List<Category> categories = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    categories.add(mapResultSetToCategory(rs));
                }
            }
        }
        return categories;
    }

    /**
     * Updates a custom category's name, type, and description.
     *
     * @param category the Category containing updated fields
     * @return true if updated, false otherwise
     * @throws SQLException if a database error occurs
     */
    public boolean updateCategory(Category category) throws SQLException {
        String sql = "UPDATE categories SET category_name = ?, category_type = ?, description = ? " +
                     "WHERE category_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, category.getCategoryName());
            stmt.setString(2, category.getCategoryType() != null ? category.getCategoryType().name() : null);
            stmt.setString(3, category.getDescription());
            stmt.setInt(4, category.getCategoryId());

            int rowsUpdated = stmt.executeUpdate();
            return rowsUpdated > 0;
        }
    }

    /**
     * Deletes a category by category_id.
     * Note: Deletion is restricted by database foreign key constraints if referenced by transactions.
     *
     * @param categoryId the category ID to delete
     * @return true if deleted, false otherwise
     * @throws SQLException if a database error occurs
     */
    public boolean deleteCategory(int categoryId) throws SQLException {
        String sql = "DELETE FROM categories WHERE category_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, categoryId);
            int rowsDeleted = stmt.executeUpdate();
            return rowsDeleted > 0;
        }
    }
}
