package dao;

import model.Transaction;
import model.TransactionType;
import util.DBConnection;

import java.sql.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for 'transactions' entity in FinTrack.
 *
 * CRITICAL SECURITY & INTEGRITY RULES:
 * All user-facing queries are strictly scoped by user_id (e.g. WHERE user_id = ?).
 * Operations referencing accounts enforce both user_id and account_id, ensuring
 * no cross-tenant transaction manipulation or leakage can occur.
 */
public class TransactionDAO {

    /**
     * Maps the current row of a ResultSet to a Transaction model object.
     */
    private Transaction mapResultSetToTransaction(ResultSet rs) throws SQLException {
        Transaction tx = new Transaction();
        tx.setTransactionId(rs.getLong("transaction_id"));
        tx.setUserId(rs.getInt("user_id"));
        tx.setAccountId(rs.getInt("account_id"));
        tx.setCategoryId(rs.getInt("category_id"));
        tx.setAmount(rs.getBigDecimal("amount"));

        String typeStr = rs.getString("transaction_type");
        if (typeStr != null) {
            tx.setTransactionType(TransactionType.valueOf(typeStr));
        }

        Date txDate = rs.getDate("transaction_date");
        if (txDate != null) {
            tx.setTransactionDate(txDate.toLocalDate());
        }

        tx.setDescription(rs.getString("description"));

        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            tx.setCreatedAt(ts.toInstant().atOffset(ZoneOffset.UTC));
        }

        return tx;
    }

    /**
     * Inserts a new financial transaction.
     * Enforces that the transaction belongs to the designated user and account.
     * Populates generated transaction_id.
     *
     * @param transaction the Transaction to record
     * @return the created Transaction with generated transaction_id populated
     * @throws SQLException if a database error occurs
     */
    public Transaction createTransaction(Transaction transaction) throws SQLException {
        String sql = "INSERT INTO transactions (user_id, account_id, category_id, amount, transaction_type, transaction_date, description, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP))";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, transaction.getUserId());
            stmt.setInt(2, transaction.getAccountId());
            stmt.setInt(3, transaction.getCategoryId());
            stmt.setBigDecimal(4, transaction.getAmount());
            stmt.setString(5, transaction.getTransactionType() != null ? transaction.getTransactionType().name() : null);
            stmt.setDate(6, transaction.getTransactionDate() != null ? Date.valueOf(transaction.getTransactionDate()) : null);
            stmt.setString(7, transaction.getDescription());

            if (transaction.getCreatedAt() != null) {
                stmt.setTimestamp(8, Timestamp.from(transaction.getCreatedAt().toInstant()));
            } else {
                stmt.setNull(8, Types.TIMESTAMP);
            }

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    transaction.setTransactionId(rs.getLong(1));
                }
            }
        }
        return transaction;
    }

    /**
     * Retrieves a transaction by transaction_id without user scoping.
     * (Primarily for internal administrative/testing lookup).
     *
     * @param transactionId the transaction ID
     * @return Transaction if found, null otherwise
     * @throws SQLException if a database error occurs
     */
    public Transaction findById(long transactionId) throws SQLException {
        String sql = "SELECT transaction_id, user_id, account_id, category_id, amount, transaction_type, transaction_date, description, created_at " +
                     "FROM transactions WHERE transaction_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, transactionId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToTransaction(rs);
                }
            }
        }
        return null;
    }

    /**
     * Overload supporting int ID.
     */
    public Transaction findById(int transactionId) throws SQLException {
        return findById((long) transactionId);
    }

    /**
     * User-scoped lookup: retrieves a transaction by transaction_id and user_id.
     * Guarantees that a user cannot access another user's transaction.
     *
     * @param userId the owner user ID
     * @param transactionId the transaction ID
     * @return Transaction if found and owned by user, null otherwise
     * @throws SQLException if a database error occurs
     */
    public Transaction findById(int userId, long transactionId) throws SQLException {
        String sql = "SELECT transaction_id, user_id, account_id, category_id, amount, transaction_type, transaction_date, description, created_at " +
                     "FROM transactions WHERE transaction_id = ? AND user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, transactionId);
            stmt.setInt(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToTransaction(rs);
                }
            }
        }
        return null;
    }

    /**
     * Retrieves all transactions logged by a user, ordered with latest transactions first.
     *
     * @param userId the user ID
     * @return List of transactions belonging to the user
     * @throws SQLException if a database error occurs
     */
    public List<Transaction> getTransactionsByUserId(int userId) throws SQLException {
        String sql = "SELECT transaction_id, user_id, account_id, category_id, amount, transaction_type, transaction_date, description, created_at " +
                     "FROM transactions WHERE user_id = ? ORDER BY transaction_date DESC, transaction_id DESC";
        List<Transaction> transactions = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    transactions.add(mapResultSetToTransaction(rs));
                }
            }
        }
        return transactions;
    }

    /**
     * Retrieves all transactions for a specific account, enforcing that the account
     * and transactions belong to the requesting user.
     *
     * @param userId the requesting user ID
     * @param accountId the account ID
     * @return List of transactions belonging to the user's account
     * @throws SQLException if a database error occurs
     */
    public List<Transaction> getTransactionsByAccountId(int userId, int accountId) throws SQLException {
        String sql = "SELECT transaction_id, user_id, account_id, category_id, amount, transaction_type, transaction_date, description, created_at " +
                     "FROM transactions WHERE user_id = ? AND account_id = ? " +
                     "ORDER BY transaction_date DESC, transaction_id DESC";
        List<Transaction> transactions = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.setInt(2, accountId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    transactions.add(mapResultSetToTransaction(rs));
                }
            }
        }
        return transactions;
    }

    /**
     * Retrieves transactions for a user within an inclusive date range.
     *
     * @param userId the user ID
     * @param startDate the inclusive beginning date
     * @param endDate the inclusive ending date
     * @return List of transactions matching the criteria
     * @throws SQLException if a database error occurs
     */
    public List<Transaction> getTransactionsByDateRange(int userId, LocalDate startDate, LocalDate endDate) throws SQLException {
        String sql = "SELECT transaction_id, user_id, account_id, category_id, amount, transaction_type, transaction_date, description, created_at " +
                     "FROM transactions WHERE user_id = ? AND transaction_date BETWEEN ? AND ? " +
                     "ORDER BY transaction_date DESC, transaction_id DESC";
        List<Transaction> transactions = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.setDate(2, Date.valueOf(startDate));
            stmt.setDate(3, Date.valueOf(endDate));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    transactions.add(mapResultSetToTransaction(rs));
                }
            }
        }
        return transactions;
    }

    /**
     * Updates an existing transaction.
     * Enforces user scoping in the WHERE clause: WHERE transaction_id = ? AND user_id = ?.
     *
     * @param transaction the Transaction with updated details
     * @return true if updated, false otherwise
     * @throws SQLException if a database error occurs
     */
    public boolean updateTransaction(Transaction transaction) throws SQLException {
        String sql = "UPDATE transactions SET account_id = ?, category_id = ?, amount = ?, transaction_type = ?, " +
                     "transaction_date = ?, description = ? " +
                     "WHERE transaction_id = ? AND user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, transaction.getAccountId());
            stmt.setInt(2, transaction.getCategoryId());
            stmt.setBigDecimal(3, transaction.getAmount());
            stmt.setString(4, transaction.getTransactionType() != null ? transaction.getTransactionType().name() : null);
            stmt.setDate(5, transaction.getTransactionDate() != null ? Date.valueOf(transaction.getTransactionDate()) : null);
            stmt.setString(6, transaction.getDescription());
            stmt.setLong(7, transaction.getTransactionId());
            stmt.setInt(8, transaction.getUserId());

            int rowsUpdated = stmt.executeUpdate();
            return rowsUpdated > 0;
        }
    }

    /**
     * Deletes a transaction, strictly enforcing that it belongs to the given user.
     *
     * @param userId the requesting user ID
     * @param transactionId the transaction ID to delete
     * @return true if deleted, false otherwise
     * @throws SQLException if a database error occurs
     */
    public boolean deleteTransaction(int userId, long transactionId) throws SQLException {
        String sql = "DELETE FROM transactions WHERE transaction_id = ? AND user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, transactionId);
            stmt.setInt(2, userId);

            int rowsDeleted = stmt.executeUpdate();
            return rowsDeleted > 0;
        }
    }

    /**
     * Overload accepting int transactionId.
     */
    public boolean deleteTransaction(int userId, int transactionId) throws SQLException {
        return deleteTransaction(userId, (long) transactionId);
    }
}
