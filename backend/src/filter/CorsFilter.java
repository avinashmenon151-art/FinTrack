package filter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Cross-Origin Resource Sharing (CORS) Filter configured for credentialed sessions.
 *
 * Security Requirements for Credentialed Sessions:
 *   - Wildcard '*' is strictly forbidden when Access-Control-Allow-Credentials is true.
 *   - Arbitrary Origin headers must NEVER be blindly reflected.
 *   - Only explicitly configured/trusted development origins (e.g. http://localhost:5500)
 *     are granted CORS access with credentials.
 */
public class CorsFilter implements Filter {

    private static final Set<String> DEFAULT_TRUSTED_ORIGINS = new HashSet<>(Arrays.asList(
            "http://localhost:5500",
            "http://127.0.0.1:5500",
            "http://localhost:3000",
            "http://127.0.0.1:3000",
            "http://localhost:8080",
            "http://127.0.0.1:8080"
    ));

    private final Set<String> trustedOrigins = new HashSet<>(DEFAULT_TRUSTED_ORIGINS);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String customOrigins = null;
        if (filterConfig != null) {
            customOrigins = filterConfig.getInitParameter("allowedOrigins");
        }
        if (customOrigins == null || customOrigins.trim().isEmpty()) {
            customOrigins = System.getenv("FINTRACK_ALLOWED_ORIGINS");
        }
        if (customOrigins != null && !customOrigins.trim().isEmpty()) {
            for (String origin : customOrigins.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty()) {
                    trustedOrigins.add(trimmed.toLowerCase());
                }
            }
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (response instanceof HttpServletResponse) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            HttpServletRequest httpRequest = (HttpServletRequest) request;

            String origin = httpRequest.getHeader("Origin");
            if (origin != null && !origin.trim().isEmpty()) {
                String trimmedOrigin = origin.trim();
                // Strictly verify against trusted origins list - do NOT blindly reflect arbitrary origins
                if (isTrustedOrigin(trimmedOrigin)) {
                    httpResponse.setHeader("Access-Control-Allow-Origin", trimmedOrigin);
                    httpResponse.setHeader("Access-Control-Allow-Credentials", "true");
                    httpResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                    httpResponse.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With, Accept, Cookie");
                    httpResponse.setHeader("Access-Control-Max-Age", "3600");
                    httpResponse.setHeader("Vary", "Origin");

                    // Handle preflight OPTIONS request for trusted origin
                    if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
                        httpResponse.setStatus(HttpServletResponse.SC_OK);
                        return;
                    }
                } else if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
                    // Reject preflight from untrusted origin
                    httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
            }
        }

        chain.doFilter(request, response);
    }

    private boolean isTrustedOrigin(String origin) {
        return origin != null && trustedOrigins.contains(origin.trim().toLowerCase());
    }

    @Override
    public void destroy() {
    }
}
