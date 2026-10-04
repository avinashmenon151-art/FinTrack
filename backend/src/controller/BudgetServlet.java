package controller;

import dao.BudgetDAO;
import model.Budget;
import util.AuthUtil;
import util.JsonUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller handling budget endpoints scoped strictly to the authenticated session:
 *   GET    /api/budgets
 *   GET    /api/budgets/{id}
 *   POST   /api/budgets
 *   PUT    /api/budgets/{id}
 *   DELETE /api/budgets/{id}
 *
 * Automatically delegates /api/budgets/{budgetId}/categories sub-paths to BudgetCategoryServlet.
 */
public class BudgetServlet extends HttpServlet {

    private final BudgetDAO budgetDAO = new BudgetDAO();
    private final BudgetCategoryServlet budgetCategoryServlet = new BudgetCategoryServlet();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer userId = AuthUtil.getAuthenticatedUserId(req);
        if (userId == null) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

        String pathInfo = req.getPathInfo();

        // Delegate sub-resource requests: /api/budgets/{budgetId}/categories
        if (pathInfo != null && pathInfo.contains("/categories")) {
            budgetCategoryServlet.doGet(req, resp);
            return;
        }

        try {
            // Case 1: GET /api/budgets/{id}
            if (pathInfo != null && pathInfo.length() > 1) {
                String idStr = pathInfo.substring(1);
                int budgetId;
                try {
                    budgetId = Integer.parseInt(idStr);
                } catch (NumberFormatException e) {
                    JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid budget ID format");
                    return;
                }

                Budget budget = budgetDAO.findById(userId, budgetId);
                if (budget != null) {
                    JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, budget);
                } else {
                    JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Budget not found or access denied");
                }
                return;
            }

            // Case 2: GET /api/budgets - all budgets belonging to the authenticated user
            List<Budget> budgets = budgetDAO.getBudgetsByUserId(userId);
            JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, budgets);

        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error retrieving budgets: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer userId = AuthUtil.getAuthenticatedUserId(req);
        if (userId == null) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

        String pathInfo = req.getPathInfo();

        // Delegate sub-resource requests: POST /api/budgets/{budgetId}/categories
        if (pathInfo != null && pathInfo.contains("/categories")) {
            budgetCategoryServlet.doPost(req, resp);
            return;
        }

        String body = JsonUtil.readRequestBody(req);
        Map<String, Object> jsonMap = JsonUtil.parseObject(body);

        String budgetName = JsonUtil.getString(jsonMap, "budget_name");
        if (budgetName == null) budgetName = JsonUtil.getString(jsonMap, "budgetName");

        LocalDate startDate = JsonUtil.getLocalDate(jsonMap, "start_date");
        if (startDate == null) startDate = JsonUtil.getLocalDate(jsonMap, "startDate");

        LocalDate endDate = JsonUtil.getLocalDate(jsonMap, "end_date");
        if (endDate == null) endDate = JsonUtil.getLocalDate(jsonMap, "endDate");

        if (budgetName == null || budgetName.isEmpty() || startDate == null || endDate == null) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Missing required fields: 'budget_name', 'start_date', and 'end_date' are required");
            return;
        }

        if (endDate.isBefore(startDate)) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid date window: 'end_date' must be greater than or equal to 'start_date'");
            return;
        }

        // CRITICAL SECURITY: Assign owner strictly from authenticated session
        Budget budget = new Budget();
        budget.setUserId(userId);
        budget.setBudgetName(budgetName.trim());
        budget.setStartDate(startDate);
        budget.setEndDate(endDate);

        try {
            Budget created = budgetDAO.createBudget(budget);
            JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_CREATED, created);
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Failed to create budget: " + e.getMessage());
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

        // Delegate sub-resource requests: PUT /api/budgets/{budgetId}/categories/{categoryId}
        if (pathInfo != null && pathInfo.contains("/categories")) {
            budgetCategoryServlet.doPut(req, resp);
            return;
        }

        if (pathInfo == null || pathInfo.length() <= 1) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Budget ID path parameter is required (/api/budgets/{id})");
            return;
        }

        int budgetId;
        try {
            budgetId = Integer.parseInt(pathInfo.substring(1));
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid budget ID format");
            return;
        }

        try {
            // Verify budget ownership before updating
            Budget existing = budgetDAO.findById(userId, budgetId);
            if (existing == null) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Budget not found or access denied");
                return;
            }

            String body = JsonUtil.readRequestBody(req);
            Map<String, Object> jsonMap = JsonUtil.parseObject(body);

            String budgetName = JsonUtil.getString(jsonMap, "budget_name");
            if (budgetName == null) budgetName = JsonUtil.getString(jsonMap, "budgetName");
            LocalDate startDate = JsonUtil.getLocalDate(jsonMap, "start_date");
            if (startDate == null) startDate = JsonUtil.getLocalDate(jsonMap, "startDate");
            LocalDate endDate = JsonUtil.getLocalDate(jsonMap, "end_date");
            if (endDate == null) endDate = JsonUtil.getLocalDate(jsonMap, "endDate");

            if (budgetName != null && !budgetName.trim().isEmpty()) existing.setBudgetName(budgetName.trim());
            if (startDate != null) existing.setStartDate(startDate);
            if (endDate != null) existing.setEndDate(endDate);

            if (existing.getEndDate().isBefore(existing.getStartDate())) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "end_date must be greater than or equal to start_date");
                return;
            }

            boolean updated = budgetDAO.updateBudget(existing);
            if (updated) {
                JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, existing);
            } else {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to update budget");
            }

        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error updating budget: " + e.getMessage());
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

        // Delegate sub-resource requests: DELETE /api/budgets/{budgetId}/categories/{categoryId}
        if (pathInfo != null && pathInfo.contains("/categories")) {
            budgetCategoryServlet.doDelete(req, resp);
            return;
        }

        if (pathInfo == null || pathInfo.length() <= 1) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Budget ID path parameter is required (/api/budgets/{id})");
            return;
        }

        int budgetId;
        try {
            budgetId = Integer.parseInt(pathInfo.substring(1));
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid budget ID format");
            return;
        }

        try {
            Budget existing = budgetDAO.findById(userId, budgetId);
            if (existing == null) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Budget not found or access denied");
                return;
            }

            boolean deleted = budgetDAO.deleteBudget(userId, budgetId);
            if (deleted) {
                Map<String, String> msg = new HashMap<>();
                msg.put("message", "Budget " + budgetId + " deleted successfully");
                JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, msg);
            } else {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to delete budget");
            }
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error deleting budget: " + e.getMessage());
        }
    }
}
