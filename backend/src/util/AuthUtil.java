package util;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Reusable utility for HTTP session-based authentication in FinTrack.
 *
 * Enforces:
 *   - Only the integer user ID is stored in the session under AUTHENTICATED_USER_ID.
 *   - No passwords, hashes, models, or DB connections are stored in the session.
 *   - Session fixation protection on login via changeSessionId().
 */
public class AuthUtil {

    public static final String SESSION_USER_ID = "AUTHENTICATED_USER_ID";

    private AuthUtil() {
    }

    /**
     * Retrieves the authenticated user's ID from the current session.
     *
     * @param request the HTTP request
     * @return the authenticated user ID, or null if no session exists or user is not authenticated
     */
    public static Integer getAuthenticatedUserId(HttpServletRequest request) {
        if (request == null) return null;
        HttpSession session = request.getSession(false);
        if (session == null) return null;

        Object val = session.getAttribute(SESSION_USER_ID);
        if (val instanceof Integer) {
            return (Integer) val;
        }
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return null;
    }

    /**
     * Establishes an authenticated session for the specified user ID.
     * Implements session fixation protection.
     *
     * @param request the HTTP request
     * @param userId the authenticated user ID
     */
    public static void setAuthenticatedUser(HttpServletRequest request, int userId) {
        if (request == null) return;

        // Session fixation protection: change session ID if a session already exists
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            try {
                request.changeSessionId();
            } catch (Throwable t) {
                // If container does not support changeSessionId (pre-Servlet 3.1), invalidate and recreate
                oldSession.invalidate();
            }
        }

        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_USER_ID, userId);
    }

    /**
     * Clears authentication by invalidating the HTTP session.
     *
     * @param request the HTTP request
     */
    public static void clearAuthentication(HttpServletRequest request) {
        if (request == null) return;
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
