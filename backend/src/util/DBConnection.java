package util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Centralized JDBC Database Connection Utility for FinTrack.
 *
 * Provides thread-safe PostgreSQL database connections via standard JDBC.
 * Configuration values are read dynamically from environment variables,
 * falling back to default local development settings.
 *
 * Supported Environment Variables:
 *   - DB_URL:      PostgreSQL JDBC connection URL (default: jdbc:postgresql://localhost:5432/fintrack)
 *   - DB_USER:     PostgreSQL database username (default: postgres)
 *   - DB_PASSWORD: PostgreSQL user password (default: empty string, never hardcoded)
 */
public class DBConnection {

    private static final String DEFAULT_URL = "jdbc:postgresql://localhost:5432/fintrack";
    private static final String DEFAULT_USER = "postgres";
    private static final String DRIVER_CLASS = "org.postgresql.Driver";

    static {
        try {
            Class.forName(DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
            System.err.println("[DBConnection] PostgreSQL JDBC Driver not found on classpath: " + DRIVER_CLASS);
            System.err.println("[DBConnection] Ensure the PostgreSQL JDBC driver JAR is placed in WEB-INF/lib/");
        }
    }

    // Private constructor to prevent instantiation of utility class
    private DBConnection() {
    }

    /**
     * Resolves the PostgreSQL JDBC URL from the environment or default.
     *
     * @return JDBC URL string
     */
    public static String getUrl() {
        String url = System.getenv("DB_URL");
        if (url == null || url.trim().isEmpty()) {
            url = System.getProperty("fintrack.db.url", DEFAULT_URL);
        }
        return url.trim();
    }

    /**
     * Resolves the PostgreSQL database username from the environment or default.
     *
     * @return database username
     */
    public static String getUser() {
        String user = System.getenv("DB_USER");
        if (user == null || user.trim().isEmpty()) {
            user = System.getProperty("fintrack.db.user", DEFAULT_USER);
        }
        return user.trim();
    }

    /**
     * Resolves the PostgreSQL database password from the environment.
     * Never hardcodes credentials.
     *
     * @return database password
     */
    public static String getPassword() {
        String password = System.getenv("DB_PASSWORD");
        if (password == null) {
            password = System.getProperty("fintrack.db.password", "");
        }
        return password;
    }

    /**
     * Opens and returns a new JDBC connection to the FinTrack PostgreSQL database.
     *
     * @return an open {@link Connection} instance
     * @throws SQLException if connection establishment fails
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(getUrl(), getUser(), getPassword());
    }

    /**
     * Safely closes a JDBC connection, ignoring null references.
     *
     * @param conn the Connection to close
     */
    public static void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                System.err.println("[DBConnection] Error closing connection: " + e.getMessage());
            }
        }
    }

    /**
     * Utility method to test database connectivity without throwing unhandled exceptions.
     *
     * @return true if connection is successfully established and valid, false otherwise
     */
    public static boolean testConnection() {
        Connection conn = null;
        try {
            conn = getConnection();
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("[DBConnection] Connection test failed: " + e.getMessage());
            return false;
        } finally {
            closeConnection(conn);
        }
    }
}
