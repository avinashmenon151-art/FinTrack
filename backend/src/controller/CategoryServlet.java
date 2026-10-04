package controller;

import dao.CategoryDAO;
import model.Category;
import model.CategoryType;
import util.AuthUtil;
import util.JsonUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller handling category endpoints scoped strictly to the authenticated session:
 *   GET    /api/categories        (returns system categories + current user's custom categories)
 *   GET    /api/categories/system (returns shared system categories only)
 *   GET    /api/categories/{id}   (system category or current user's custom category)
 *   POST   /api/categories        (creates custom category for authenticated user)
 *   PUT    /api/categories/{id}   (only custom categories owned by authenticated user)
 *   DELETE /api/categories/{id}   (only custom categories owned by authenticated user)
 */
public class CategoryServlet extends HttpServlet {

    private final CategoryDAO categoryDAO = new CategoryDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer userId = AuthUtil.getAuthenticatedUserId(req);
        if (userId == null) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

        String pathInfo = req.getPathInfo();

        try {
            // Case 1: GET /api/categories/system - shared system categories
            if (pathInfo != null && "/system".equalsIgnoreCase(pathInfo.trim())) {
                List<Category> systemCategories = categoryDAO.getSystemCategories();
                JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, systemCategories);
                return;
            }

            // Case 2: GET /api/categories/{id}
            if (pathInfo != null && pathInfo.length() > 1 && !"/system".equalsIgnoreCase(pathInfo.trim())) {
                String idStr = pathInfo.substring(1);
                int categoryId;
                try {
                    categoryId = Integer.parseInt(idStr);
                } catch (NumberFormatException e) {
                    JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid category ID path parameter");
                    return;
                }

                Category category = categoryDAO.findById(categoryId);
                if (category == null) {
                    JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Category not found");
                    return;
                }

                // If custom category, ensure it belongs to the authenticated user.
                // A user must never see another user's custom categories.
                if (category.getUserId() != null && !category.getUserId().equals(userId)) {
                    JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Category not found");
                    return;
                }

                JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, category);
                return;
            }

            // Case 3: GET /api/categories - returns system categories + current user's custom categories
            List<Category> available = categoryDAO.getAvailableCategoriesForUser(userId);
            JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, available);

        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error retrieving categories: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer userId = AuthUtil.getAuthenticatedUserId(req);
        if (userId == null) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

        String body = JsonUtil.readRequestBody(req);
        Map<String, Object> jsonMap = JsonUtil.parseObject(body);

        String categoryName = JsonUtil.getString(jsonMap, "category_name");
        if (categoryName == null) categoryName = JsonUtil.getString(jsonMap, "categoryName");

        String categoryTypeStr = JsonUtil.getString(jsonMap, "category_type");
        if (categoryTypeStr == null) categoryTypeStr = JsonUtil.getString(jsonMap, "categoryType");

        String description = JsonUtil.getString(jsonMap, "description");

        if (categoryName == null || categoryName.isEmpty() || categoryTypeStr == null || categoryTypeStr.isEmpty()) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Missing required fields: 'category_name' and 'category_type' are required");
            return;
        }

        CategoryType categoryType;
        try {
            categoryType = CategoryType.valueOf(categoryTypeStr.trim());
        } catch (IllegalArgumentException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid category_type. Allowed values: 'Income', 'Expense'");
            return;
        }

        // CRITICAL SECURITY: Assign owner strictly to the authenticated user
        Category category = new Category();
        category.setUserId(userId);
        category.setCategoryName(categoryName.trim());
        category.setCategoryType(categoryType);
        category.setDescription(description != null ? description.trim() : null);

        try {
            Category created = categoryDAO.createCategory(category);
            JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_CREATED, created);
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Failed to create category: " + e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer userId = AuthUtil.getAuthenticatedUserId(req);
        if (userId == null) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.length() <= 1) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Category ID path parameter is required (/api/categories/{id})");
            return;
        }

        int categoryId;
        try {
            categoryId = Integer.parseInt(pathInfo.substring(1));
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid category ID format");
            return;
        }

        try {
            Category existing = categoryDAO.findById(categoryId);
            if (existing == null) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Category not found");
                return;
            }

            // System categories cannot be modified by users
            if (existing.getUserId() == null) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "System categories cannot be modified");
                return;
            }

            // Ownership check: users may only modify their own custom categories
            if (!existing.getUserId().equals(userId)) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Category not found or access denied");
                return;
            }

            String body = JsonUtil.readRequestBody(req);
            Map<String, Object> jsonMap = JsonUtil.parseObject(body);

            String categoryName = JsonUtil.getString(jsonMap, "category_name");
            if (categoryName == null) categoryName = JsonUtil.getString(jsonMap, "categoryName");

            String categoryTypeStr = JsonUtil.getString(jsonMap, "category_type");
            if (categoryTypeStr == null) categoryTypeStr = JsonUtil.getString(jsonMap, "categoryType");

            String description = JsonUtil.getString(jsonMap, "description");

            if (categoryName != null && !categoryName.trim().isEmpty()) existing.setCategoryName(categoryName.trim());
            if (categoryTypeStr != null && !categoryTypeStr.trim().isEmpty()) {
                try {
                    existing.setCategoryType(CategoryType.valueOf(categoryTypeStr.trim()));
                } catch (IllegalArgumentException e) {
                    JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid category_type. Allowed: 'Income', 'Expense'");
                    return;
                }
            }
            if (description != null) existing.setDescription(description.trim());

            boolean updated = categoryDAO.updateCategory(existing);
            if (updated) {
                JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, existing);
            } else {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to update category");
            }

        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error updating category: " + e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer userId = AuthUtil.getAuthenticatedUserId(req);
        if (userId == null) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.length() <= 1) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Category ID path parameter is required (/api/categories/{id})");
            return;
        }

        int categoryId;
        try {
            categoryId = Integer.parseInt(pathInfo.substring(1));
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid category ID format");
            return;
        }

        try {
            Category existing = categoryDAO.findById(categoryId);
            if (existing == null) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Category not found");
                return;
            }

            // System categories cannot be deleted
            if (existing.getUserId() == null) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "System categories cannot be deleted");
                return;
            }

            // Ownership check: only owner can delete custom category
            if (!existing.getUserId().equals(userId)) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Category not found or access denied");
                return;
            }

            boolean deleted = categoryDAO.deleteCategory(categoryId);
            if (deleted) {
                Map<String, String> msg = new HashMap<>();
                msg.put("message", "Custom category " + categoryId + " deleted successfully");
                JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, msg);
            } else {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to delete category");
            }
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error deleting category: " + e.getMessage());
        }
    }
}
