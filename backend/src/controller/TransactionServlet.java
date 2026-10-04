package controller;

import dao.AccountDAO;
import dao.CategoryDAO;
import dao.TransactionDAO;
import model.Account;
import model.Category;
import model.Transaction;
import model.TransactionType;
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
 * Controller handling transaction endpoints scoped strictly to the authenticated session:
 *   GET    /api/transactions
 *   GET    /api/transactions?account_id={accountId}
 *   GET    /api/transactions?start_date=YYYY-MM-DD&end_date=YYYY-MM-DD
 *   GET    /api/transactions/{id}
 *   POST   /api/transactions
 *   PUT    /api/transactions/{id}
 *   DELETE /api/transactions/{id}
 */
public class TransactionServlet extends HttpServlet {

    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final AccountDAO accountDAO = new AccountDAO();
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
            // Case 1: GET /api/transactions/{id}
            if (pathInfo != null && pathInfo.length() > 1) {
                String idStr = pathInfo.substring(1);
                long transactionId;
                try {
                    transactionId = Long.parseLong(idStr);
                } catch (NumberFormatException e) {
                    JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid transaction ID format");
                    return;
                }

                Transaction tx = transactionDAO.findById(userId, transactionId);
                if (tx != null) {
                    JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, tx);
                } else {
                    JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Transaction not found");
                }
                return;
            }

            // Case 2: Filter by Account: GET /api/transactions?account_id={accountId}
            String accountIdParam = req.getParameter("account_id");
            if (accountIdParam != null && !accountIdParam.trim().isEmpty()) {
                int accountId;
                try {
                    accountId = Integer.parseInt(accountIdParam.trim());
                } catch (NumberFormatException e) {
                    JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid 'account_id' parameter format");
                    return;
                }

                // Verify account belongs to authenticated user
                Account acc = accountDAO.findById(accountId);
                if (acc == null || !acc.getUserId().equals(userId)) {
                    JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Account not found or access denied");
                    return;
                }

                List<Transaction> list = transactionDAO.getTransactionsByAccountId(userId, accountId);
                JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, list);
                return;
            }

            // Case 3: Filter by Date Range: GET /api/transactions?start_date=...&end_date=...
            String startDateParam = req.getParameter("start_date");
            String endDateParam = req.getParameter("end_date");
            if (startDateParam != null && endDateParam != null) {
                LocalDate start;
                LocalDate end;
                try {
                    start = LocalDate.parse(startDateParam.trim());
                    end = LocalDate.parse(endDateParam.trim());
                } catch (Exception e) {
                    JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid date format. Expected YYYY-MM-DD");
                    return;
                }

                if (end.isBefore(start)) {
                    JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "end_date must be greater than or equal to start_date");
                    return;
                }

                List<Transaction> list = transactionDAO.getTransactionsByDateRange(userId, start, end);
                JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, list);
                return;
            }

            // Case 4: Default GET /api/transactions
            List<Transaction> list = transactionDAO.getTransactionsByUserId(userId);
            JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, list);

        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error retrieving transactions: " + e.getMessage());
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

        Integer categoryId = JsonUtil.getInteger(jsonMap, "category_id");
        if (categoryId == null) categoryId = JsonUtil.getInteger(jsonMap, "categoryId");

        BigDecimal amount = JsonUtil.getBigDecimal(jsonMap, "amount");
        String typeStr = JsonUtil.getString(jsonMap, "transaction_type");
        if (typeStr == null) typeStr = JsonUtil.getString(jsonMap, "transactionType");

        LocalDate txDate = JsonUtil.getLocalDate(jsonMap, "transaction_date");
        if (txDate == null) txDate = JsonUtil.getLocalDate(jsonMap, "transactionDate");

        String description = JsonUtil.getString(jsonMap, "description");

        if (accountId == null || categoryId == null || amount == null || typeStr == null || txDate == null) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Missing required fields: 'account_id', 'category_id', 'amount', 'transaction_type', and 'transaction_date' are required");
            return;
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Transaction amount must be strictly greater than 0");
            return;
        }

        TransactionType txType;
        try {
            txType = TransactionType.valueOf(typeStr.trim());
        } catch (IllegalArgumentException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid transaction_type. Allowed values: 'Income', 'Expense'");
            return;
        }

        try {
            // 1. Verify account ownership
            Account account = accountDAO.findById(accountId);
            if (account == null || !account.getUserId().equals(userId)) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "Account not found or does not belong to the authenticated user");
                return;
            }

            // 2. Verify category: allow system category (user_id IS NULL) or authenticated user's custom category
            Category category = categoryDAO.findById(categoryId);
            if (category == null || (category.getUserId() != null && !category.getUserId().equals(userId))) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "Category not found or does not belong to the authenticated user");
                return;
            }

            // 3. Bind user_id strictly from session
            Transaction tx = new Transaction();
            tx.setUserId(userId);
            tx.setAccountId(accountId);
            tx.setCategoryId(categoryId);
            tx.setAmount(amount);
            tx.setTransactionType(txType);
            tx.setTransactionDate(txDate);
            tx.setDescription(description != null ? description.trim() : null);

            Transaction created = transactionDAO.createTransaction(tx);
            JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_CREATED, created);

        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Failed to record transaction: " + e.getMessage());
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
                    "Transaction ID path parameter is required (/api/transactions/{id})");
            return;
        }

        long transactionId;
        try {
            transactionId = Long.parseLong(pathInfo.substring(1));
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid transaction ID format");
            return;
        }

        try {
            // Strict ownership verification: only retrieve transaction if it belongs to authenticated user
            Transaction existing = transactionDAO.findById(userId, transactionId);
            if (existing == null) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Transaction not found or access denied");
                return;
            }

            String body = JsonUtil.readRequestBody(req);
            Map<String, Object> jsonMap = JsonUtil.parseObject(body);

            Integer accountId = JsonUtil.getInteger(jsonMap, "account_id");
            if (accountId == null) accountId = JsonUtil.getInteger(jsonMap, "accountId");
            Integer categoryId = JsonUtil.getInteger(jsonMap, "category_id");
            if (categoryId == null) categoryId = JsonUtil.getInteger(jsonMap, "categoryId");
            BigDecimal amount = JsonUtil.getBigDecimal(jsonMap, "amount");
            String typeStr = JsonUtil.getString(jsonMap, "transaction_type");
            if (typeStr == null) typeStr = JsonUtil.getString(jsonMap, "transactionType");
            LocalDate txDate = JsonUtil.getLocalDate(jsonMap, "transaction_date");
            if (txDate == null) txDate = JsonUtil.getLocalDate(jsonMap, "transactionDate");
            String description = JsonUtil.getString(jsonMap, "description");

            if (accountId != null) {
                Account account = accountDAO.findById(accountId);
                if (account == null || !account.getUserId().equals(userId)) {
                    JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                            "Target account does not belong to the authenticated user");
                    return;
                }
                existing.setAccountId(accountId);
            }

            if (categoryId != null) {
                Category category = categoryDAO.findById(categoryId);
                if (category == null || (category.getUserId() != null && !category.getUserId().equals(userId))) {
                    JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                            "Target category not found or does not belong to the authenticated user");
                    return;
                }
                existing.setCategoryId(categoryId);
            }

            if (amount != null) {
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Amount must be strictly greater than 0");
                    return;
                }
                existing.setAmount(amount);
            }

            if (typeStr != null) {
                try {
                    existing.setTransactionType(TransactionType.valueOf(typeStr.trim()));
                } catch (IllegalArgumentException e) {
                    JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid transaction_type: " + typeStr);
                    return;
                }
            }

            if (txDate != null) existing.setTransactionDate(txDate);
            if (description != null) existing.setDescription(description.trim());

            boolean updated = transactionDAO.updateTransaction(existing);
            if (updated) {
                JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, existing);
            } else {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to update transaction");
            }

        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error updating transaction: " + e.getMessage());
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
                    "Transaction ID path parameter is required (/api/transactions/{id})");
            return;
        }

        long transactionId;
        try {
            transactionId = Long.parseLong(pathInfo.substring(1));
        } catch (NumberFormatException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid transaction ID format");
            return;
        }

        try {
            Transaction existing = transactionDAO.findById(userId, transactionId);
            if (existing == null) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Transaction not found or access denied");
                return;
            }

            boolean deleted = transactionDAO.deleteTransaction(userId, transactionId);
            if (deleted) {
                Map<String, String> msg = new HashMap<>();
                msg.put("message", "Transaction " + transactionId + " deleted successfully");
                JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, msg);
            } else {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to delete transaction");
            }
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error deleting transaction: " + e.getMessage());
        }
    }
}
