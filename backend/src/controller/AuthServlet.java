package controller;

import dao.UserDAO;
import model.User;
import util.AuthUtil;
import org.mindrot.jbcrypt.BCrypt;
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
 * Controller handling authentication and session lifecycle endpoints:
 *   POST /api/auth/register - Register a new user with BCrypt password hashing
 *   POST /api/auth/login    - Authenticate user, start session with fixation protection
 *   POST /api/auth/logout   - Invalidate session
 *   GET  /api/auth/me       - Return current session-authenticated user profile
 */
public class AuthServlet extends HttpServlet {

    private final UserDAO userDAO = new UserDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = cleanPath(req.getPathInfo());

        if ("/me".equals(path)) {
            handleMe(req, resp);
        } else {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Endpoint not found: GET /api/auth" + path);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = cleanPath(req.getPathInfo());

        switch (path) {
            case "/register":
                handleRegister(req, resp);
                break;
            case "/login":
                handleLogin(req, resp);
                break;
            case "/logout":
                handleLogout(req, resp);
                break;
            default:
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Endpoint not found: POST /api/auth" + path);
                break;
        }
    }

    /**
     * POST /api/auth/register
     * 1. Validate required fields (name, email, password)
     * 2. Validate email is not already registered
     * 3. Hash password with BCrypt (cost factor 12)
     * 4. Persist User in database
     * 5. Return safe user information (never password_hash)
     */
    private void handleRegister(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = JsonUtil.readRequestBody(req);
        Map<String, Object> jsonMap = JsonUtil.parseObject(body);

        String name = JsonUtil.getString(jsonMap, "name");
        String email = JsonUtil.getString(jsonMap, "email");
        String password = JsonUtil.getString(jsonMap, "password");
        String phoneNumber = JsonUtil.getString(jsonMap, "phone_number");
        if (phoneNumber == null) {
            phoneNumber = JsonUtil.getString(jsonMap, "phoneNumber");
        }

        // 1. Validate required fields
        if (name == null || name.isEmpty() || email == null || email.isEmpty() || password == null || password.isEmpty()) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Missing required fields: 'name', 'email', and 'password' are required");
            return;
        }

        String normalizedEmail = email.trim().toLowerCase();

        try {
            // 2. Validate email is not already registered
            User existing = userDAO.findByEmail(normalizedEmail);
            if (existing != null) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_CONFLICT,
                        "Email is already registered");
                return;
            }

            // 3. Hash password using BCrypt (never plaintext)
            String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(12));

            // 4. Create and persist User
            User newUser = new User();
            newUser.setName(name.trim());
            newUser.setEmail(normalizedEmail);
            newUser.setPasswordHash(passwordHash);
            newUser.setPhoneNumber(phoneNumber != null ? phoneNumber.trim() : null);
            newUser.setDefaultAccountId(null);

            User created = userDAO.createUser(newUser);

            // 5. Return safe user representation (password_hash is stripped by JsonUtil)
            JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_CREATED, created);

        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("unique")) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_CONFLICT, "Email is already registered");
            } else {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Database error during registration: " + e.getMessage());
            }
        }
    }

    /**
     * POST /api/auth/login
     * 1. Receive email and password
     * 2. Find user by email
     * 3. Verify BCrypt password
     * 4. Create authenticated HttpSession and store AUTHENTICATED_USER_ID
     * 5. Return safe user information (never password_hash)
     * On failure: returns 401 Unauthorized without revealing if email exists.
     */
    private void handleLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = JsonUtil.readRequestBody(req);
        Map<String, Object> jsonMap = JsonUtil.parseObject(body);

        String email = JsonUtil.getString(jsonMap, "email");
        String password = JsonUtil.getString(jsonMap, "password");

        if (email == null || email.isEmpty() || password == null || password.isEmpty()) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Both 'email' and 'password' are required");
            return;
        }

        String normalizedEmail = email.trim().toLowerCase();

        try {
            User user = userDAO.findByEmail(normalizedEmail);

            // Verify credentials
            if (user == null || !BCrypt.checkpw(password, user.getPasswordHash())) {
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED,
                        "Invalid email or password");
                return;
            }

            // Create authenticated session and store user ID with session fixation protection
            AuthUtil.setAuthenticatedUser(req, user.getUserId());

            // Return safe user information
            JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, user);

        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error during authentication: " + e.getMessage());
        }
    }

    /**
     * POST /api/auth/logout
     * Invalidates the session and returns a success response.
     * Safely handles situations where session is already invalid or non-existent.
     */
    private void handleLogout(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        AuthUtil.clearAuthentication(req);

        Map<String, String> responseMap = new HashMap<>();
        responseMap.put("message", "Logged out successfully");
        JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, responseMap);
    }

    /**
     * GET /api/auth/me
     * Returns the currently authenticated user's profile.
     * Requires an active session; returns 401 Unauthorized if missing.
     */
    private void handleMe(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Integer userId = AuthUtil.getAuthenticatedUserId(req);
        if (userId == null) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED,
                    "Authentication required. No active session found.");
            return;
        }

        try {
            User user = userDAO.findById(userId);
            if (user == null) {
                // User may have been removed from the database
                AuthUtil.clearAuthentication(req);
                JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED,
                        "Authenticated user account no longer exists.");
                return;
            }

            JsonUtil.sendJsonResponse(resp, HttpServletResponse.SC_OK, user);

        } catch (SQLException e) {
            JsonUtil.sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error retrieving user profile: " + e.getMessage());
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
