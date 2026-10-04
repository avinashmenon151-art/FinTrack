package dao;

import model.Account;
import util.DBConnection;

import java.sql.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for 'accounts' entity in FinTrack.
 * Handles financial account creation, retrieval, balance updates, and default account designation.
 */
public class AccountDAO {

    /**
     * Maps the current row of a ResultSet to an Account model object.
     */
    private Account mapResultSetToAccount(ResultSet rs) throws SQLException {
        Account account = new Account();
        account.setAccountId(rs.getInt("account_id"));
        account.setUserId(rs.getInt("user_id"));
        account.setAccountName(rs.getString("account_name"));
        account.setAccountType(rs.getString("account_type"));
        account.setBalance(rs.getBigDecimal("balance"));

        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            account.setCreatedAt(ts.toInstant().atOffset(ZoneOffset.UTC));
        }

        return account;
    }

    /**
     * Creates a new financial account for a user.
     * Sets the generated account_id on the account object.
     *
     * @param account the Account to create
     * @return the created Account with generated account_id
     * @throws SQLException if a database error occurs
     */
    public Account createAccount(Account account) throws SQLException {
        String sql = "INSERT INTO accounts (user_id, account_name, account_type, balance, created_at) " +
                     "VALUES (?, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP))";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, account.getUserId());
            stmt.setString(2, account.getAccountName());
            stmt.setString(3, account.getAccountType());
            stmt.setBigDecimal(4, account.getBalance());

            if (account.getCreatedAt() != null) {
                stmt.setTimestamp(5, Timestamp.from(account.getCreatedAt().toInstant()));
            } else {
                stmt.setNull(5, Types.TIMESTAMP);
            }

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    account.setAccountId(rs.getInt(1));
                }
            }
        }
        return account;
    }

    /**
     * Retrieves an account by its unique primary key (account_id).
     *
     * @param accountId the account ID to search for
     * @return Account if found, null otherwise
     * @throws SQLException if a database error occurs
     */
    public Account findById(int accountId) throws SQLException {
        String sql = "SELECT account_id, user_id, account_name, account_type, balance, created_at " +
                     "FROM accounts WHERE account_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, accountId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToAccount(rs);
                }
            }
        }
        return null;
    }

    /**
     * Retrieves all accounts belonging to a specific user.
     *
     * @param userId the owner user ID
     * @return List of accounts owned by the user
     * @throws SQLException if a database error occurs
     */
    public List<Account> getAccountsByUserId(int userId) throws SQLException {
        String sql = "SELECT account_id, user_id, account_name, account_type, balance, created_at " +
                     "FROM accounts WHERE user_id = ? ORDER BY account_id ASC";
        List<Account> accounts = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    accounts.add(mapResultSetToAccount(rs));
                }
            }
        }
        return accounts;
    }

    /**
     * Updates an account's name, type, and balance.
     * Scoped by account_id and user_id to enforce ownership.
     *
     * @param account the Account containing updated values
     * @return true if updated, false otherwise
     * @throws SQLException if a database error occurs
     */
    public boolean updateAccount(Account account) throws SQLException {
        String sql = "UPDATE accounts SET account_name = ?, account_type = ?, balance = ? " +
                     "WHERE account_id = ? AND user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, account.getAccountName());
            stmt.setString(2, account.getAccountType());
            stmt.setBigDecimal(3, account.getBalance());
            stmt.setInt(4, account.getAccountId());
            stmt.setInt(5, account.getUserId());

            int rowsUpdated = stmt.executeUpdate();
            return rowsUpdated > 0;
        }
    }

    /**
     * Deletes an account by account_id.
     *
     * @param accountId the account ID to delete
     * @return true if deleted, false otherwise
     * @throws SQLException if a database error occurs
     */
    public boolean deleteAccount(int accountId) throws SQLException {
        String sql = "DELETE FROM accounts WHERE account_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, accountId);
            int rowsDeleted = stmt.executeUpdate();
            return rowsDeleted > 0;
        }
    }

    /**
     * Designates an account as the user's default account.
     *
     * IMPORTANT SECURITY RULE:
     * Guarantees that the account actually belongs to the user requesting the change
     * by verifying WHERE account_id = ? AND user_id = ? before updating users.default_account_id.
     * Cross-user assignment is strictly prohibited.
     *
     * @param userId the user ID
     * @param accountId the account ID to designate as default
     * @return true if successfully verified and set as default, false otherwise
     * @throws SQLException if a database error occurs
     */
    public boolean setDefaultAccount(int userId, int accountId) throws SQLException {
        // Atomic update enforcing ownership check inside the WHERE clause
        String sql = "UPDATE users " +
                     "SET default_account_id = ? " +
                     "WHERE user_id = ? " +
                     "  AND EXISTS (" +
                     "      SELECT 1 FROM accounts WHERE account_id = ? AND user_id = ?" +
                     "  )";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, accountId);
            stmt.setInt(2, userId);
            stmt.setInt(3, accountId);
            stmt.setInt(4, userId);

            int rowsUpdated = stmt.executeUpdate();
            return rowsUpdated > 0;
        }
    }
}
