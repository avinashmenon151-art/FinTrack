package controller;

import dao.UserDAO;
import model.User;
import util.AuthUtil;
import util.BCrypt;
import util.JsonUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller handling user management endpoints scoped strictly to the authenticated user:
 *   GET    /api/users/me
 *   GET    /api/users/{id} (only permitted if {id} == authenticated user ID)
 *   PUT    /api/users/me
 *   PUT    /api/users/{id} (only permitted if {id} == authenticated user ID)
 *   DELETE /api/users/me
 *   DELETE /api/users/{id} (only permitted if {id} == authenticated user ID)
 */
public class UserServlet extends HttpServlet {

    private final UserDAO userDAO = new UserDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer authUserId = AuthUtil.getAuthenticatedUserId(req);
        if (authUserId == null) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

        String pathInfo = cleanPath(req.getPathInfo());

        try {
            // Case 1: GET /api/users or GET /api/users/me
            if (pathInfo.isEmpty() || "/me".equalsIgnoreCase(pathInfo)) {
                User user = userDAO.findById(authUserId);
                if (user != null) {
                    JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, user);
                } else {
                    JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "User not found");
                }
                return;
            }

            // Case 2: GET /api/users/{id}
            String idStr = pathInfo.substring(1);
            int requestedId;
            try {
                requestedId = Integer.parseInt(idStr);
            } catch (NumberFormatException e) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid user ID format: " + idStr);
                return;
            }

            // Enforce ownership: users may only access their own user profile
            if (requestedId != authUserId) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_FORBIDDEN, "Forbidden: Cannot access another user's profile");
                return;
            }

            User user = userDAO.findById(authUserId);
            if (user != null) {
                JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, user);
            } else {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "User not found with ID: " + authUserId);
            }

        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error retrieving user: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // Direct creation via /api/users is redirected to the official registration endpoint
        JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                "Please use POST /api/auth/register for new user registration");
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer authUserId = AuthUtil.getAuthenticatedUserId(req);
        if (authUserId == null) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

        String pathInfo = cleanPath(req.getPathInfo());
        if (pathInfo.isEmpty()) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Target user required (/api/users/me or /api/users/{id})");
            return;
        }

        int targetUserId;
        if ("/me".equalsIgnoreCase(pathInfo)) {
            targetUserId = authUserId;
        } else {
            try {
                targetUserId = Integer.parseInt(pathInfo.substring(1));
            } catch (NumberFormatException e) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid user ID format");
                return;
            }
        }

        // Strict ownership verification
        if (targetUserId != authUserId) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_FORBIDDEN, "Forbidden: Cannot modify another user's profile");
            return;
        }

        String body = JsonUtil.readRequestBody(req);
        Map<String, Object> jsonMap = JsonUtil.parseObject(body);

        try {
            User existing = userDAO.findById(authUserId);
            if (existing == null) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "User not found with ID: " + authUserId);
                return;
            }

            String name = JsonUtil.getString(jsonMap, "name");
            String email = JsonUtil.getString(jsonMap, "email");
            String password = JsonUtil.getString(jsonMap, "password");
            String phone = JsonUtil.getString(jsonMap, "phone_number");
            if (phone == null) phone = JsonUtil.getString(jsonMap, "phoneNumber");
            Integer defaultAccountId = JsonUtil.getInteger(jsonMap, "default_account_id");
            if (defaultAccountId == null) defaultAccountId = JsonUtil.getInteger(jsonMap, "defaultAccountId");

            if (name != null && !name.trim().isEmpty()) existing.setName(name.trim());
            if (email != null && !email.trim().isEmpty()) existing.setEmail(email.trim().toLowerCase());
            if (phone != null) existing.setPhoneNumber(phone.trim());

            // If updating password, hash with BCrypt (never store plaintext)
            if (password != null && !password.trim().isEmpty()) {
                existing.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt(12)));
            }

            if (jsonMap.containsKey("default_account_id") || jsonMap.containsKey("defaultAccountId")) {
                existing.setDefaultAccountId(defaultAccountId);
            }

            boolean updated = userDAO.updateUser(existing);
            if (updated) {
                JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, existing);
            } else {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to update user profile");
            }
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error updating user: " + e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer authUserId = AuthUtil.getAuthenticatedUserId(req);
        if (authUserId == null) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

        String pathInfo = cleanPath(req.getPathInfo());
        if (pathInfo.isEmpty()) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Target user required (/api/users/me or /api/users/{id})");
            return;
        }

        int targetUserId;
        if ("/me".equalsIgnoreCase(pathInfo)) {
            targetUserId = authUserId;
        } else {
            try {
                targetUserId = Integer.parseInt(pathInfo.substring(1));
            } catch (NumberFormatException e) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid user ID format");
                return;
            }
        }

        // Strict ownership verification
        if (targetUserId != authUserId) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_FORBIDDEN, "Forbidden: Cannot delete another user's account");
            return;
        }

        try {
            boolean deleted = userDAO.deleteUser(authUserId);
            if (deleted) {
                // Clear session identity upon account deletion
                AuthUtil.clearAuthentication(req);
                Map<String, String> msg = new HashMap<>();
                msg.put("message", "User account deleted successfully");
                JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, msg);
            } else {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "User not found with ID: " + authUserId);
            }
        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error deleting user: " + e.getMessage());
        }
    }

    private String cleanPath(String pathInfo) {
        if (pathInfo == null || pathInfo.trim().isEmpty()) {
            return "";
        }
        String p = pathInfo.trim();
        if (p.endsWith("/") && p.length() > 1) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }
}
