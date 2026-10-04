package filter;

import util.AuthUtil;
import util.JsonUtil;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Filter that enforces HTTP session authentication across all protected API endpoints in FinTrack.
 *
 * Protected endpoints:
 *   /api/users/*
 *   /api/accounts/*
 *   /api/categories/*
 *   /api/transactions/*
 *   /api/budgets/*
 *   /api/budget-categories/*
 *   /api/savings-goals/*
 *   /api/auth/me
 *
 * Public endpoints:
 *   /api/auth/register
 *   /api/auth/login
 *   /api/auth/logout (safely handles both active and expired sessions)
 *
 * If no valid session exists for a protected endpoint, immediately returns 401 Unauthorized
 * without invoking downstream DAOs or business logic.
 */
public class AuthenticationFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Allow preflight CORS OPTIONS requests without authentication
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String uri = httpRequest.getRequestURI();
        String contextPath = httpRequest.getContextPath();
        String path = (contextPath != null && !contextPath.isEmpty()) ? uri.substring(contextPath.length()) : uri;

        // Check if endpoint is public
        if (isPublicEndpoint(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Verify session-based authenticated user identity
        Integer authenticatedUserId = AuthUtil.getAuthenticatedUserId(httpRequest);
        if (authenticatedUserId == null) {
            JsonUtil.sendErrorResponse(httpResponse, HttpServletResponse.SC_UNAUTHORIZED,
                    "Authentication required. No active session found.");
            return;
        }

        // Authenticated user exists; proceed down the filter chain
        chain.doFilter(request, response);
    }

    /**
     * Determines whether the given path is a public endpoint that does not require an active session.
     */
    private boolean isPublicEndpoint(String path) {
        if (path == null) return false;
        String cleanPath = path;
        if (cleanPath.endsWith("/") && cleanPath.length() > 1) {
            cleanPath = cleanPath.substring(0, cleanPath.length() - 1);
        }

        if (cleanPath.equals("/api/auth/register") ||
            cleanPath.equals("/api/auth/login") ||
            cleanPath.equals("/api/auth/logout")) {
            return true;
        }

        // Any non-API request (if any) is allowed
        return !cleanPath.startsWith("/api/");
    }

    @Override
    public void destroy() {
    }
}
