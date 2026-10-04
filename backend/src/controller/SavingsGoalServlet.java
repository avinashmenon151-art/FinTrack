package controller;

import dao.AccountDAO;
import dao.SavingsGoalDAO;
import model.Account;
import model.SavingsGoal;
import model.SavingsGoalStatus;
import util.AuthUtil;
import util.JsonUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller handling savings goal endpoints scoped strictly to the authenticated session:
 *   GET    /api/savings-goals
 *   GET    /api/savings-goals/{id}
 *   POST   /api/savings-goals
 *   PUT    /api/savings-goals/{id}
 *   DELETE /api/savings-goals/{id}
 */
public class SavingsGoalServlet extends HttpServlet {

    private final SavingsGoalDAO savingsGoalDAO = new SavingsGoalDAO();
    private final AccountDAO accountDAO = new AccountDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer userId = AuthUtil.getAuthenticatedUserId(req);
        if (userId == null) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

        String pathInfo = req.getPathInfo();

        try {
            // Case 1: GET /api/savings-goals/{id}
            if (pathInfo != null && pathInfo.length() > 1) {
                String idStr = pathInfo.substring(1);
                int goalId;
                try {
                    goalId = Integer.parseInt(idStr);
                } catch (NumberFormatException e) {
                    JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid goal ID format");
                    return;
                }

                SavingsGoal goal = savingsGoalDAO.findById(userId, goalId);
                if (goal != null) {
                    JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, goal);
                } else {
                    JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Savings goal not found or access denied");
                }
                return;
            }

            // Case 2: GET /api/savings-goals - all goals belonging to the authenticated user
            List<SavingsGoal> goals = savingsGoalDAO.getGoalsByUserId(userId);
            JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, goals);

        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error retrieving savings goals: " + e.getMessage());
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

        Integer accountId = JsonUtil.getInteger(jsonMap, "account_id");
        if (accountId == null) accountId = JsonUtil.getInteger(jsonMap, "accountId");

        String goalName = JsonUtil.getString(jsonMap, "goal_name");
        if (goalName == null) goalName = JsonUtil.getString(jsonMap, "goalName");

        BigDecimal targetAmount = JsonUtil.getBigDecimal(jsonMap, "target_amount");
        if (targetAmount == null) targetAmount = JsonUtil.getBigDecimal(jsonMap, "targetAmount");

        BigDecimal savedAmount = JsonUtil.getBigDecimal(jsonMap, "saved_amount");
        if (savedAmount == null) savedAmount = JsonUtil.getBigDecimal(jsonMap, "savedAmount");
        if (savedAmount == null) savedAmount = BigDecimal.ZERO;

        LocalDate targetDate = JsonUtil.getLocalDate(jsonMap, "target_date");
        if (targetDate == null) targetDate = JsonUtil.getLocalDate(jsonMap, "targetDate");

        String statusStr = JsonUtil.getString(jsonMap, "status");
        if (statusStr == null) statusStr = "Active";

        if (goalName == null || goalName.isEmpty() || targetAmount == null) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Missing required fields: 'goal_name' and 'target_amount' are required");
            return;
        }

        if (targetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "target_amount must be strictly greater than 0");
            return;
        }

        if (savedAmount.compareTo(BigDecimal.ZERO) < 0) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "saved_amount cannot be negative");
            return;
        }

        SavingsGoalStatus status;
        try {
            status = SavingsGoalStatus.valueOf(statusStr.trim());
        } catch (IllegalArgumentException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid status. Allowed values: 'Active', 'Completed', 'Cancelled'");
            return;
        }

        try {
            // CRITICAL SECURITY: If account_id is supplied, verify it belongs to this authenticated user
            if (accountId != null) {
                Account account = accountDAO.findById(accountId);
                if (account == null || !account.getUserId().equals(userId)) {
                    JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                            "Linked account not found or does not belong to the authenticated user");
                    return;
                }
            }

            // Bind user_id strictly from session
            SavingsGoal goal = new SavingsGoal();
            goal.setUserId(userId);
            goal.setAccountId(accountId);
            goal.setGoalName(goalName.trim());
            goal.setTargetAmount(targetAmount);
            goal.setSavedAmount(savedAmount);
            goal.setTargetDate(targetDate);
            goal.setStatus(status);

            SavingsGoal created = savingsGoalDAO.createSavingsGoal(goal);
            JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_CREATED, created);

        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Failed to create savings goal: " + e.getMessage());
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
                    "Goal ID path parameter is required (/api/savings-goals/{id})");
            return;
        }

        int goalId;
        try {
            goalId = Integer.parseInt(pathInfo.substring(1));
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid goal ID format");
            return;
        }

        try {
            // Verify goal ownership before updating
            SavingsGoal existing = savingsGoalDAO.findById(userId, goalId);
            if (existing == null) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Savings goal not found or access denied");
                return;
            }

            String body = JsonUtil.readRequestBody(req);
            Map<String, Object> jsonMap = JsonUtil.parseObject(body);

            Integer accountId = JsonUtil.getInteger(jsonMap, "account_id");
            if (accountId == null) accountId = JsonUtil.getInteger(jsonMap, "accountId");
            String goalName = JsonUtil.getString(jsonMap, "goal_name");
            if (goalName == null) goalName = JsonUtil.getString(jsonMap, "goalName");
            BigDecimal targetAmount = JsonUtil.getBigDecimal(jsonMap, "target_amount");
            if (targetAmount == null) targetAmount = JsonUtil.getBigDecimal(jsonMap, "targetAmount");
            BigDecimal savedAmount = JsonUtil.getBigDecimal(jsonMap, "saved_amount");
            if (savedAmount == null) savedAmount = JsonUtil.getBigDecimal(jsonMap, "savedAmount");
            LocalDate targetDate = JsonUtil.getLocalDate(jsonMap, "target_date");
            if (targetDate == null) targetDate = JsonUtil.getLocalDate(jsonMap, "targetDate");
            String statusStr = JsonUtil.getString(jsonMap, "status");

            if (jsonMap.containsKey("account_id") || jsonMap.containsKey("accountId")) {
                if (accountId != null) {
                    Account acc = accountDAO.findById(accountId);
                    if (acc == null || !acc.getUserId().equals(userId)) {
                        JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                                "Linked account not found or does not belong to the authenticated user");
                        return;
                    }
                }
                existing.setAccountId(accountId);
            }

            if (goalName != null && !goalName.trim().isEmpty()) existing.setGoalName(goalName.trim());
            if (targetAmount != null) {
                if (targetAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "target_amount must be strictly greater than 0");
                    return;
                }
                existing.setTargetAmount(targetAmount);
            }
            if (savedAmount != null) {
                if (savedAmount.compareTo(BigDecimal.ZERO) < 0) {
                    JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "saved_amount cannot be negative");
                    return;
                }
                existing.setSavedAmount(savedAmount);
            }
            if (targetDate != null) existing.setTargetDate(targetDate);
            if (statusStr != null) {
                try {
                    existing.setStatus(SavingsGoalStatus.valueOf(statusStr.trim()));
                } catch (IllegalArgumentException e) {
                    JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                            "Invalid status. Allowed values: 'Active', 'Completed', 'Cancelled'");
                    return;
                }
            }

            boolean updated = savingsGoalDAO.updateSavingsGoal(existing);
            if (updated) {
                JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, existing);
            } else {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to update savings goal");
            }

        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error updating savings goal: " + e.getMessage());
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
                    "Goal ID path parameter is required (/api/savings-goals/{id})");
            return;
        }

        int goalId;
        try {
            goalId = Integer.parseInt(pathInfo.substring(1));
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid goal ID format");
            return;
        }

        try {
            SavingsGoal existing = savingsGoalDAO.findById(userId, goalId);
            if (existing == null) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Savings goal not found or access denied");
                return;
            }

            boolean deleted = savingsGoalDAO.deleteSavingsGoal(userId, goalId);
            if (deleted) {
                Map<String, String> msg = new HashMap<>();
                msg.put("message", "Savings goal " + goalId + " deleted successfully");
                JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, msg);
            } else {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to delete savings goal");
            }
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error deleting savings goal: " + e.getMessage());
        }
    }
}
