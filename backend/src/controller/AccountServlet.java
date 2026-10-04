package controller;

import dao.AccountDAO;
import model.Account;
import util.AuthUtil;
import util.JsonUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller handling financial account endpoints scoped strictly to the authenticated session:
 *   GET    /api/accounts
 *   GET    /api/accounts/{id}
 *   POST   /api/accounts
 *   PUT    /api/accounts/{id}
 *   PUT    /api/accounts/{id}/default
 *   DELETE /api/accounts/{id}
 */
public class AccountServlet extends HttpServlet {

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
            // Case 1: GET /api/accounts/{id}
            if (pathInfo != null && pathInfo.length() > 1) {
                String idStr = pathInfo.substring(1);
                int accountId;
                try {
                    accountId = Integer.parseInt(idStr);
                } catch (NumberFormatException e) {
                    JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid account ID path parameter");
                    return;
                }

                Account account = accountDAO.findById(accountId);
                // Strict ownership verification: only return account if it belongs to authenticated user
                if (account != null && account.getUserId().equals(userId)) {
                    JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, account);
                } else {
                    JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Account not found");
                }
                return;
            }

            // Case 2: GET /api/accounts - list all accounts belonging to the authenticated user
            List<Account> accounts = accountDAO.getAccountsByUserId(userId);
            JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, accounts);

        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error retrieving accounts: " + e.getMessage());
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

        String accountName = JsonUtil.getString(jsonMap, "account_name");
        if (accountName == null) accountName = JsonUtil.getString(jsonMap, "accountName");

        String accountType = JsonUtil.getString(jsonMap, "account_type");
        if (accountType == null) accountType = JsonUtil.getString(jsonMap, "accountType");

        BigDecimal balance = JsonUtil.getBigDecimal(jsonMap, "balance");
        if (balance == null) {
            balance = BigDecimal.ZERO;
        }

        if (accountName == null || accountName.isEmpty() || accountType == null || accountType.isEmpty()) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Missing required fields: 'account_name' and 'account_type' are required");
            return;
        }

        // CRITICAL SECURITY: Assign owner strictly from authenticated session
        Account account = new Account();
        account.setUserId(userId);
        account.setAccountName(accountName.trim());
        account.setAccountType(accountType.trim().toUpperCase());
        account.setBalance(balance);

        try {
            Account created = accountDAO.createAccount(account);
            JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_CREATED, created);
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Failed to create account: " + e.getMessage());
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
                    "Account ID path parameter is required (/api/accounts/{id})");
            return;
        }

        String[] parts = pathInfo.substring(1).split("/");
        int accountId;
        try {
            accountId = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid account ID format");
            return;
        }

        try {
            // Verify existing account belongs to authenticated user
            Account existing = accountDAO.findById(accountId);
            if (existing == null || !existing.getUserId().equals(userId)) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Account not found or access denied");
                return;
            }

            // Sub-action: PUT /api/accounts/{id}/default
            if (parts.length > 1 && "default".equalsIgnoreCase(parts[1])) {
                boolean success = accountDAO.setDefaultAccount(userId, accountId);
                if (success) {
                    Map<String, Object> res = new HashMap<>();
                    res.put("message", "Account " + accountId + " successfully designated as default");
                    res.put("defaultAccountId", accountId);
                    res.put("default_account_id", accountId);
                    JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, res);
                } else {
                    JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Failed to set default account");
                }
                return;
            }

            // Standard Update: PUT /api/accounts/{id}
            String body = JsonUtil.readRequestBody(req);
            Map<String, Object> jsonMap = JsonUtil.parseObject(body);

            String accountName = JsonUtil.getString(jsonMap, "account_name");
            if (accountName == null) accountName = JsonUtil.getString(jsonMap, "accountName");
            String accountType = JsonUtil.getString(jsonMap, "account_type");
            if (accountType == null) accountType = JsonUtil.getString(jsonMap, "accountType");
            BigDecimal balance = JsonUtil.getBigDecimal(jsonMap, "balance");

            if (accountName != null && !accountName.trim().isEmpty()) existing.setAccountName(accountName.trim());
            if (accountType != null && !accountType.trim().isEmpty()) existing.setAccountType(accountType.trim().toUpperCase());
            if (balance != null) existing.setBalance(balance);

            boolean updated = accountDAO.updateAccount(existing);
            if (updated) {
                JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, existing);
            } else {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to update account");
            }

        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error updating account: " + e.getMessage());
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
                    "Account ID path parameter is required (/api/accounts/{id})");
            return;
        }

        int accountId;
        try {
            accountId = Integer.parseInt(pathInfo.substring(1));
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid account ID format");
            return;
        }

        try {
            // Strict ownership verification: only allow deleting own account
            Account account = accountDAO.findById(accountId);
            if (account == null || !account.getUserId().equals(userId)) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Account not found or access denied");
                return;
            }

            boolean deleted = accountDAO.deleteAccount(accountId);
            if (deleted) {
                Map<String, String> msg = new HashMap<>();
                msg.put("message", "Account " + accountId + " deleted successfully");
                JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, msg);
            } else {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to delete account");
            }
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error deleting account: " + e.getMessage());
        }
    }
}
