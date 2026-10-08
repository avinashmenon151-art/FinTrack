package cli;

import dao.UserDAO;
import model.User;
import java.sql.SQLException;

/**
 * Manages runtime authentication state for the active CLI session.
 * Stores the authenticated User and guarantees user isolation.
 */
public class SessionState {

    private static final SessionState INSTANCE = new SessionState();

    public static SessionState getInstance() {
        return INSTANCE;
    }

    private User currentUser;

    public SessionState() {
        this.currentUser = null;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public void login(User user) {
        this.currentUser = user;
    }

    public void setUser(User user) {
        this.currentUser = user;
    }

    public void logout() {
        this.currentUser = null;
    }

    public void clear() {
        this.currentUser = null;
    }

    public User getUser() {
        return currentUser;
    }

    public int getUserId() {
        if (currentUser == null) {
            throw new IllegalStateException("No active user session.");
        }
        return currentUser.getUserId();
    }

    public String getUserName() {
        return currentUser != null ? currentUser.getName() : "Guest";
    }

    public String getEmail() {
        return currentUser != null ? currentUser.getEmail() : "";
    }

    public Integer getDefaultAccountId() {
        return currentUser != null ? currentUser.getDefaultAccountId() : null;
    }

    /**
     * Refreshes the user profile from the database to reflect changes
     * such as updated default accounts.
     */
    public void refreshUser(UserDAO userDAO) {
        if (currentUser == null) return;
        try {
            User refreshed = userDAO.findById(currentUser.getUserId());
            if (refreshed != null) {
                this.currentUser = refreshed;
            }
        } catch (SQLException ignored) {
        }
    }
}
