package controller;

import dao.BudgetCategoryDAO;
import dao.BudgetDAO;
import dao.CategoryDAO;
import model.Budget;
import model.BudgetCategory;
import model.Category;
import util.AuthUtil;
import util.JsonUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller handling budget-category allocation mappings scoped strictly to the authenticated session:
 *   GET    /api/budget-categories/{budgetId}
 *   POST   /api/budget-categories/{budgetId}
 *   PUT    /api/budget-categories/{budgetId}/{categoryId}
 *   DELETE /api/budget-categories/{budgetId}/{categoryId}
 *
 * Also callable via delegation from BudgetServlet (/api/budgets/{budgetId}/categories).
 */
public class BudgetCategoryServlet extends HttpServlet {

    private final BudgetCategoryDAO budgetCategoryDAO = new BudgetCategoryDAO();
    private final BudgetDAO budgetDAO = new BudgetDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer userId = AuthUtil.getAuthenticatedUserId(req);
        if (userId == null) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.length() <= 1) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Budget ID path parameter is required (/api/budget-categories/{budgetId})");
            return;
        }

        String[] parts = cleanParts(pathInfo);
        if (parts.length < 1) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Budget ID is required");
            return;
        }

        int budgetId;
        try {
            budgetId = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid budget ID format");
            return;
        }

        try {
            // CRITICAL SECURITY: Verify budget belongs to authenticated user before viewing allocations
            Budget budget = budgetDAO.findById(userId, budgetId);
            if (budget == null) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Budget not found or access denied");
                return;
            }

            List<BudgetCategory> categories = budgetCategoryDAO.getCategoriesForBudget(budgetId);
            JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, categories);
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error retrieving budget categories: " + e.getMessage());
        }
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer userId = AuthUtil.getAuthenticatedUserId(req);
        if (userId == null) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.length() <= 1) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Budget ID path parameter is required");
            return;
        }

        String[] parts = cleanParts(pathInfo);
        if (parts.length < 1) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Budget ID is required");
            return;
        }

        int budgetId;
        try {
            budgetId = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid budget ID format");
            return;
        }

        String body = JsonUtil.readRequestBody(req);
        Map<String, Object> jsonMap = JsonUtil.parseObject(body);

        Integer categoryId = JsonUtil.getInteger(jsonMap, "category_id");
        if (categoryId == null) categoryId = JsonUtil.getInteger(jsonMap, "categoryId");
        BigDecimal allocatedAmount = JsonUtil.getBigDecimal(jsonMap, "allocated_amount");
        if (allocatedAmount == null) allocatedAmount = JsonUtil.getBigDecimal(jsonMap, "allocatedAmount");

        if (categoryId == null || allocatedAmount == null) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Missing required fields: 'category_id' and 'allocated_amount'");
            return;
        }

        if (allocatedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "allocated_amount must be strictly greater than 0");
            return;
        }

        try {
            // 1. CRITICAL SECURITY: Verify budget belongs to authenticated user
            Budget budget = budgetDAO.findById(userId, budgetId);
            if (budget == null) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Budget not found or access denied");
                return;
            }

            // 2. CRITICAL SECURITY: Verify category belongs to user or is shared system category
            Category category = categoryDAO.findById(categoryId);
            if (category == null || (category.getUserId() != null && !category.getUserId().equals(userId))) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "Category not found or access denied");
                return;
            }

            BudgetCategory bc = new BudgetCategory(budgetId, categoryId, allocatedAmount);
            boolean created = budgetCategoryDAO.addCategoryToBudget(bc);
            if (created) {
                JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_CREATED, bc);
            } else {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to map category to budget");
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("unique")) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_CONFLICT,
                        "Category " + categoryId + " is already allocated in budget " + budgetId);
            } else {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Database error mapping category to budget: " + e.getMessage());
            }
        }
    }

    @Override
    public void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer userId = AuthUtil.getAuthenticatedUserId(req);
        if (userId == null) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

        String pathInfo = req.getPathInfo();
        String[] parts = cleanParts(pathInfo);

        if (parts.length < 2) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Budget ID and Category ID are required (/api/budget-categories/{budgetId}/{categoryId})");
            return;
        }

        int budgetId;
        int categoryId;
        try {
            budgetId = Integer.parseInt(parts[0]);
            categoryId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid numeric ID format");
            return;
        }

        String body = JsonUtil.readRequestBody(req);
        Map<String, Object> jsonMap = JsonUtil.parseObject(body);

        BigDecimal allocatedAmount = JsonUtil.getBigDecimal(jsonMap, "allocated_amount");
        if (allocatedAmount == null) allocatedAmount = JsonUtil.getBigDecimal(jsonMap, "allocatedAmount");

        if (allocatedAmount == null || allocatedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "allocated_amount must be strictly greater than 0");
            return;
        }

        try {
            // CRITICAL SECURITY: Verify budget belongs to authenticated user
            Budget budget = budgetDAO.findById(userId, budgetId);
            if (budget == null) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Budget not found or access denied");
                return;
            }

            boolean updated = budgetCategoryDAO.updateAllocatedAmount(budgetId, categoryId, allocatedAmount);
            if (updated) {
                BudgetCategory bc = new BudgetCategory(budgetId, categoryId, allocatedAmount);
                JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, bc);
            } else {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Budget-category mapping not found");
            }
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error updating allocation: " + e.getMessage());
        }
    }

    @Override
    public void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer userId = AuthUtil.getAuthenticatedUserId(req);
        if (userId == null) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

        String pathInfo = req.getPathInfo();
        String[] parts = cleanParts(pathInfo);

        if (parts.length < 2) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Budget ID and Category ID are required (/api/budget-categories/{budgetId}/{categoryId})");
            return;
        }

        int budgetId;
        int categoryId;
        try {
            budgetId = Integer.parseInt(parts[0]);
            categoryId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid numeric ID format");
            return;
        }

        try {
            // CRITICAL SECURITY: Verify budget belongs to authenticated user
            Budget budget = budgetDAO.findById(userId, budgetId);
            if (budget == null) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Budget not found or access denied");
                return;
            }

            boolean deleted = budgetCategoryDAO.removeCategoryFromBudget(budgetId, categoryId);
            if (deleted) {
                Map<String, String> msg = new HashMap<>();
                msg.put("message", "Category " + categoryId + " removed from budget " + budgetId);
                JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, msg);
            } else {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Budget-category mapping not found");
            }
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error removing category: " + e.getMessage());
        }
    }

    private String[] cleanParts(String pathInfo) {
        if (pathInfo == null) return new String[0];
        String s = pathInfo.trim();
        if (s.startsWith("/")) s = s.substring(1);
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        String[] raw = s.split("/");
        List<String> list = new ArrayList<>();
        for (String r : raw) {
            if (!"categories".equalsIgnoreCase(r) && !r.isEmpty()) {
                list.add(r);
            }
        }
        return list.toArray(new String[0]);
    }
}
