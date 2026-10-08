package cli;

import dao.AccountDAO;
import dao.CategoryDAO;
import dao.TransactionDAO;
import model.Account;
import model.Category;
import model.Transaction;
import model.TransactionType;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Transaction management view for FinTrack CLI.
 * Handles ledger browsing, filtering by account and date range,
 * recording new transactions, modifying, and deleting.
 * Strictly guarantees multi-tenant isolation and ownership checks.
 */
public class TransactionsView {

    private final SessionState session;
    private final TransactionDAO transactionDAO;
    private final AccountDAO accountDAO;
    private final CategoryDAO categoryDAO;

    public TransactionsView(SessionState session, TransactionDAO transactionDAO, AccountDAO accountDAO, CategoryDAO categoryDAO) {
        this.session = session;
        this.transactionDAO = transactionDAO;
        this.accountDAO = accountDAO;
        this.categoryDAO = categoryDAO;
    }

    public void run() {
        while (true) {
            ConsoleUtil.printSubHeader("TRANSACTION MANAGEMENT");
            System.out.println("1. List All Transactions");
            System.out.println("2. Filter Transactions by Account");
            System.out.println("3. Filter Transactions by Date Range");
            System.out.println("4. Record New Transaction (Income / Expense)");
            System.out.println("5. Update Existing Transaction");
            System.out.println("6. Delete Transaction");
            System.out.println("7. Return to Main Menu");

            int choice = ConsoleUtil.promptInt("Enter choice", 1, 7);
            switch (choice) {
                case 1:
                    listAllTransactions();
                    ConsoleUtil.pause();
                    break;
                case 2:
                    filterByAccount();
                    ConsoleUtil.pause();
                    break;
                case 3:
                    filterByDateRange();
                    ConsoleUtil.pause();
                    break;
                case 4:
                    addTransaction();
                    break;
                case 5:
                    updateTransaction();
                    break;
                case 6:
                    deleteTransaction();
                    break;
                case 7:
                    return;
            }
        }
    }

    private void renderTransactionsTable(List<Transaction> transactions) throws SQLException {
        if (transactions.isEmpty()) {
            ConsoleUtil.printInfo("No transactions found matching criteria.");
            return;
        }

        // Preload user's accounts and categories for human-readable names
        Map<Integer, String> accountNames = new HashMap<>();
        for (Account a : accountDAO.getAccountsByUserId(session.getUserId())) {
            accountNames.put(a.getAccountId(), a.getAccountName());
        }

        Map<Integer, String> categoryNames = new HashMap<>();
        for (Category c : categoryDAO.getAvailableCategoriesForUser(session.getUserId())) {
            categoryNames.put(c.getCategoryId(), c.getCategoryName());
        }

        System.out.println(String.format(" %-6s | %-10s | %-7s | %-14s | %-16s | %-18s | %s",
                "ID", "Date", "Type", "Amount", "Category", "Account", "Description"));
        ConsoleUtil.printDivider();

        for (Transaction tx : transactions) {
            String accName = accountNames.getOrDefault(tx.getAccountId(), "#" + tx.getAccountId());
            String catName = categoryNames.getOrDefault(tx.getCategoryId(), "#" + tx.getCategoryId());
            String desc = tx.getDescription() != null ? tx.getDescription() : "—";
            String sign = tx.getTransactionType() == TransactionType.Income ? "+" : "-";

            System.out.println(String.format(" #%-5d | %s | %-7s | %14s | %-16s | %-18s | %s",
                    tx.getTransactionId(),
                    ConsoleUtil.formatDate(tx.getTransactionDate()),
                    tx.getTransactionType(),
                    sign + ConsoleUtil.formatCurrency(tx.getAmount()),
                    ConsoleUtil.padRight(catName, 16),
                    ConsoleUtil.padRight(accName, 18),
                    desc));
        }
    }

    private void listAllTransactions() {
        try {
            List<Transaction> list = transactionDAO.getTransactionsByUserId(session.getUserId());
            ConsoleUtil.printSubHeader("YOUR TRANSACTION HISTORY (" + list.size() + ")");
            renderTransactionsTable(list);
        } catch (SQLException e) {
            ConsoleUtil.printError("Failed to fetch transactions: " + e.getMessage());
        }
    }

    private void filterByAccount() {
        try {
            List<Account> accounts = accountDAO.getAccountsByUserId(session.getUserId());
            if (accounts.isEmpty()) {
                ConsoleUtil.printWarning("You do not have any accounts created yet.");
                return;
            }

            System.out.println("\nSelect Account to filter:");
            for (int i = 0; i < accounts.size(); i++) {
                Account a = accounts.get(i);
                System.out.println((i + 1) + ". #" + a.getAccountId() + " " + a.getAccountName() + " (" + a.getAccountType() + ")");
            }

            int sel = ConsoleUtil.promptInt("Choice", 1, accounts.size());
            Account selected = accounts.get(sel - 1);

            List<Transaction> list = transactionDAO.getTransactionsByAccountId(session.getUserId(), selected.getAccountId());
            ConsoleUtil.printSubHeader("TRANSACTIONS FOR ACCOUNT: " + selected.getAccountName() + " (" + list.size() + ")");
            renderTransactionsTable(list);
        } catch (SQLException e) {
            ConsoleUtil.printError("Error filtering by account: " + e.getMessage());
        }
    }

    private void filterByDateRange() {
        ConsoleUtil.printSubHeader("FILTER TRANSACTIONS BY DATE RANGE");
        LocalDate startDate = ConsoleUtil.promptLocalDate("Start Date", false, LocalDate.now().minusMonths(1));
        LocalDate endDate = ConsoleUtil.promptLocalDate("End Date", false, LocalDate.now());

        if (endDate.isBefore(startDate)) {
            ConsoleUtil.printError("End date cannot be prior to start date.");
            return;
        }

        try {
            List<Transaction> list = transactionDAO.getTransactionsByDateRange(session.getUserId(), startDate, endDate);
            ConsoleUtil.printSubHeader("TRANSACTIONS (" + ConsoleUtil.formatDate(startDate) + " to " + ConsoleUtil.formatDate(endDate) + ") - Total: " + list.size());
            renderTransactionsTable(list);
        } catch (SQLException e) {
            ConsoleUtil.printError("Error filtering by date: " + e.getMessage());
        }
    }

    private void addTransaction() {
        ConsoleUtil.printSubHeader("RECORD NEW TRANSACTION");
        try {
            // 1. Select user-owned Account
            List<Account> accounts = accountDAO.getAccountsByUserId(session.getUserId());
            if (accounts.isEmpty()) {
                ConsoleUtil.printWarning("You must create an account first before logging transactions.");
                ConsoleUtil.pause();
                return;
            }

            System.out.println("\nSelect Account:");
            for (int i = 0; i < accounts.size(); i++) {
                Account a = accounts.get(i);
                System.out.println((i + 1) + ". #" + a.getAccountId() + " " + a.getAccountName() + " (Balance: " + ConsoleUtil.formatCurrency(a.getBalance()) + ")");
            }
            int accChoice = ConsoleUtil.promptInt("Choice", 1, accounts.size());
            Account chosenAccount = accounts.get(accChoice - 1);

            // 2. Select Type
            System.out.println("\nSelect Transaction Type:");
            System.out.println("1. Expense");
            System.out.println("2. Income");
            int typeChoice = ConsoleUtil.promptInt("Choice", 1, 2);
            TransactionType type = (typeChoice == 1) ? TransactionType.Expense : TransactionType.Income;

            // 3. Select Category matching type
            List<Category> categories = categoryDAO.getAvailableCategoriesForUser(session.getUserId());
            System.out.println("\nSelect Category for " + type + ":");
            int validCount = 0;
            Map<Integer, Category> selectionMap = new HashMap<>();
            for (Category c : categories) {
                // Filter matching category type or allow general
                if (c.getCategoryType() == null || c.getCategoryType().name().equalsIgnoreCase(type.name())) {
                    validCount++;
                    selectionMap.put(validCount, c);
                    String badge = (c.getUserId() == null) ? "[System]" : "[Custom]";
                    System.out.println(validCount + ". " + c.getCategoryName() + " " + badge);
                }
            }
            if (validCount == 0) {
                ConsoleUtil.printError("No categories available for " + type + ".");
                ConsoleUtil.pause();
                return;
            }
            int catChoice = ConsoleUtil.promptInt("Choice", 1, validCount);
            Category chosenCategory = selectionMap.get(catChoice);

            // 4. Amount (strictly > 0)
            BigDecimal amount = ConsoleUtil.promptBigDecimal("Amount (₹)", false, true);

            // 5. Date
            LocalDate date = ConsoleUtil.promptLocalDate("Transaction Date", false, LocalDate.now());

            // 6. Description
            String description = ConsoleUtil.promptOptionalString("Description / Notes", "");

            Transaction tx = new Transaction();
            tx.setUserId(session.getUserId());
            tx.setAccountId(chosenAccount.getAccountId());
            tx.setCategoryId(chosenCategory.getCategoryId());
            tx.setTransactionType(type);
            tx.setAmount(amount);
            tx.setTransactionDate(date);
            tx.setDescription(description);

            Transaction created = transactionDAO.createTransaction(tx);
            ConsoleUtil.printSuccess("Transaction recorded with ID #" + created.getTransactionId() + "!");

        } catch (SQLException e) {
            ConsoleUtil.printError("Failed to record transaction: " + e.getMessage());
        }
        ConsoleUtil.pause();
    }

    private void updateTransaction() {
        ConsoleUtil.printSubHeader("UPDATE TRANSACTION");
        long txId = (long) ConsoleUtil.promptInt("Enter Transaction ID to update (or 0 to cancel)", 0, Integer.MAX_VALUE);
        if (txId == 0) return;

        try {
            // Strictly scoped lookup to enforce ownership
            Transaction existing = transactionDAO.findById(session.getUserId(), txId);
            if (existing == null) {
                ConsoleUtil.printError("Transaction #" + txId + " not found or does not belong to your account.");
                ConsoleUtil.pause();
                return;
            }

            System.out.println("\nExisting Transaction Details:");
            System.out.println("  Date:        " + ConsoleUtil.formatDate(existing.getTransactionDate()));
            System.out.println("  Type:        " + existing.getTransactionType());
            System.out.println("  Amount:      " + ConsoleUtil.formatCurrency(existing.getAmount()));
            System.out.println("  Description: " + (existing.getDescription() != null ? existing.getDescription() : ""));
            ConsoleUtil.printDivider();
            System.out.println("Leave inputs blank to retain existing values.\n");

            // Option to change account
            List<Account> accounts = accountDAO.getAccountsByUserId(session.getUserId());
            System.out.println("Account (0 to keep current account):");
            for (int i = 0; i < accounts.size(); i++) {
                Account a = accounts.get(i);
                String cur = a.getAccountId().equals(existing.getAccountId()) ? " [CURRENT]" : "";
                System.out.println((i + 1) + ". #" + a.getAccountId() + " " + a.getAccountName() + cur);
            }
            int accChoice = ConsoleUtil.promptInt("Choice", 0, accounts.size());
            if (accChoice > 0) {
                existing.setAccountId(accounts.get(accChoice - 1).getAccountId());
            }

            // Option to change type
            System.out.println("\nType: 1. Keep " + existing.getTransactionType() + " | 2. Expense | 3. Income");
            int tChoice = ConsoleUtil.promptInt("Choice", 1, 3);
            if (tChoice == 2) existing.setTransactionType(TransactionType.Expense);
            if (tChoice == 3) existing.setTransactionType(TransactionType.Income);

            // Option to change category
            List<Category> categories = categoryDAO.getAvailableCategoriesForUser(session.getUserId());
            System.out.println("\nCategory (0 to keep current category):");
            for (int i = 0; i < categories.size(); i++) {
                Category c = categories.get(i);
                String cur = c.getCategoryId().equals(existing.getCategoryId()) ? " [CURRENT]" : "";
                System.out.println((i + 1) + ". " + c.getCategoryName() + cur);
            }
            int catChoice = ConsoleUtil.promptInt("Choice", 0, categories.size());
            if (catChoice > 0) {
                existing.setCategoryId(categories.get(catChoice - 1).getCategoryId());
            }

            // Amount
            BigDecimal newAmount = ConsoleUtil.promptOptionalBigDecimal("New Amount", existing.getAmount());
            existing.setAmount(newAmount);

            // Date
            LocalDate newDate = ConsoleUtil.promptLocalDate("New Date", true, existing.getTransactionDate());
            existing.setTransactionDate(newDate);

            // Description
            String newDesc = ConsoleUtil.promptOptionalString("New Description", existing.getDescription());
            existing.setDescription(newDesc);

            boolean ok = transactionDAO.updateTransaction(existing);
            if (ok) {
                ConsoleUtil.printSuccess("Transaction #" + txId + " updated successfully!");
            } else {
                ConsoleUtil.printError("Failed to update transaction.");
            }

        } catch (SQLException e) {
            ConsoleUtil.printError("Database error during update: " + e.getMessage());
        }
        ConsoleUtil.pause();
    }

    private void deleteTransaction() {
        ConsoleUtil.printSubHeader("DELETE TRANSACTION");
        long txId = (long) ConsoleUtil.promptInt("Enter Transaction ID to delete (or 0 to cancel)", 0, Integer.MAX_VALUE);
        if (txId == 0) return;

        try {
            // Strictly scoped lookup to enforce ownership
            Transaction existing = transactionDAO.findById(session.getUserId(), txId);
            if (existing == null) {
                ConsoleUtil.printError("Transaction #" + txId + " not found or does not belong to your account.");
                ConsoleUtil.pause();
                return;
            }

            System.out.println("Target: " + ConsoleUtil.formatDate(existing.getTransactionDate()) +
                    " | " + existing.getTransactionType() + " | " + ConsoleUtil.formatCurrency(existing.getAmount()) +
                    " | " + (existing.getDescription() != null ? existing.getDescription() : ""));

            boolean confirm = ConsoleUtil.promptConfirm("Are you sure you want to delete this transaction?");
            if (!confirm) {
                ConsoleUtil.printInfo("Deletion cancelled.");
                ConsoleUtil.pause();
                return;
            }

            boolean ok = transactionDAO.deleteTransaction(session.getUserId(), txId);
            if (ok) {
                ConsoleUtil.printSuccess("Transaction #" + txId + " deleted successfully!");
            } else {
                ConsoleUtil.printError("Failed to delete transaction.");
            }

        } catch (SQLException e) {
            ConsoleUtil.printError("Database error during deletion: " + e.getMessage());
        }
        ConsoleUtil.pause();
    }
}
