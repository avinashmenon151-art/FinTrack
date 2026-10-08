package cli;

import dao.AccountDAO;
import dao.UserDAO;
import model.Account;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/**
 * Account management CLI view.
 * Enables listing, creating, updating, deleting, and setting default accounts.
 * Strictly scopes all operations to the authenticated user's ID.
 */
public class AccountsView {

    private final SessionState session;
    private final AccountDAO accountDAO;
    private final UserDAO userDAO;

    private static final List<String> ACCOUNT_TYPES = Arrays.asList(
            "Checking",
            "Savings",
            "Cash",
            "Credit Card",
            "Investment",
            "Loan",
            "Other"
    );

    public AccountsView(SessionState session, AccountDAO accountDAO, UserDAO userDAO) {
        this.session = session;
        this.accountDAO = accountDAO;
        this.userDAO = userDAO;
    }

    public void run() {
        while (true) {
            ConsoleUtil.printSubHeader("ACCOUNT MANAGEMENT");
            System.out.println("1. List Accounts");
            System.out.println("2. Create New Account");
            System.out.println("3. Update Account Details");
            System.out.println("4. Delete Account");
            System.out.println("5. Designate Default Account");
            System.out.println("6. Return to Main Menu");

            int choice = ConsoleUtil.promptInt("Enter choice", 1, 6);
            switch (choice) {
                case 1:
                    listAccounts();
                    ConsoleUtil.pause();
                    break;
                case 2:
                    createAccount();
                    break;
                case 3:
                    updateAccount();
                    break;
                case 4:
                    deleteAccount();
                    break;
                case 5:
                    setDefaultAccount();
                    break;
                case 6:
                    return;
            }
        }
    }

    private void listAccounts() {
        try {
            session.refreshUser(userDAO);
            List<Account> accounts = accountDAO.getAccountsByUserId(session.getUserId());

            ConsoleUtil.printSubHeader("YOUR FINANCIAL ACCOUNTS (" + accounts.size() + ")");
            if (accounts.isEmpty()) {
                ConsoleUtil.printInfo("No accounts found. Create your first account using option 2.");
                return;
            }

            System.out.println(String.format(" %-5s | %-24s | %-14s | %-16s | %s",
                    "ID", "Account Name", "Type", "Balance", "Status"));
            ConsoleUtil.printDivider();

            for (Account acc : accounts) {
                boolean isDefault = session.getDefaultAccountId() != null && session.getDefaultAccountId().equals(acc.getAccountId());
                String status = isDefault ? "[Default]" : "";
                System.out.println(String.format(" #%-4d | %-24s | %-14s | %16s | %s",
                        acc.getAccountId(),
                        acc.getAccountName(),
                        acc.getAccountType(),
                        ConsoleUtil.formatCurrency(acc.getBalance()),
                        status));
            }
        } catch (SQLException e) {
            ConsoleUtil.printError("Failed to retrieve accounts: " + e.getMessage());
        }
    }

    private void createAccount() {
        ConsoleUtil.printSubHeader("CREATE NEW ACCOUNT");
        String name = ConsoleUtil.promptString("Account Name (e.g. HDFC Salary, Emergency Savings)");

        System.out.println("\nSelect Account Type:");
        for (int i = 0; i < ACCOUNT_TYPES.size(); i++) {
            System.out.println((i + 1) + ". " + ACCOUNT_TYPES.get(i));
        }
        int typeIdx = ConsoleUtil.promptInt("Choice", 1, ACCOUNT_TYPES.size());
        String type = ACCOUNT_TYPES.get(typeIdx - 1);

        BigDecimal balance = ConsoleUtil.promptBigDecimal("Initial Balance (₹)", true, true);

        Account account = new Account();
        account.setUserId(session.getUserId());
        account.setAccountName(name);
        account.setAccountType(type);
        account.setBalance(balance);

        try {
            Account created = accountDAO.createAccount(account);
            ConsoleUtil.printSuccess("Account created successfully with ID #" + created.getAccountId() + "!");

            // If user has no default account, set this one automatically
            if (session.getDefaultAccountId() == null) {
                accountDAO.setDefaultAccount(session.getUserId(), created.getAccountId());
                session.refreshUser(userDAO);
                ConsoleUtil.printInfo("Designated as your primary default account.");
            }
        } catch (SQLException e) {
            ConsoleUtil.printError("Could not create account: " + e.getMessage());
        }
        ConsoleUtil.pause();
    }

    private void updateAccount() {
        ConsoleUtil.printSubHeader("UPDATE ACCOUNT");
        listAccounts();

        int accountId = ConsoleUtil.promptInt("Enter Account ID to update (or 0 to cancel)", 0, Integer.MAX_VALUE);
        if (accountId == 0) return;

        try {
            Account existing = accountDAO.findById(accountId);
            if (existing == null || !existing.getUserId().equals(session.getUserId())) {
                ConsoleUtil.printError("Account #" + accountId + " not found or does not belong to you.");
                ConsoleUtil.pause();
                return;
            }

            System.out.println("\nUpdating Account: " + existing.getAccountName() + " (" + existing.getAccountType() + ")");
            System.out.println("Leave input blank to retain existing value.");

            String newName = ConsoleUtil.promptOptionalString("New Account Name", existing.getAccountName());

            System.out.println("\nAccount Types (0 to keep '" + existing.getAccountType() + "'):");
            for (int i = 0; i < ACCOUNT_TYPES.size(); i++) {
                System.out.println((i + 1) + ". " + ACCOUNT_TYPES.get(i));
            }
            int typeChoice = ConsoleUtil.promptInt("Select Type", 0, ACCOUNT_TYPES.size());
            String newType = typeChoice > 0 ? ACCOUNT_TYPES.get(typeChoice - 1) : existing.getAccountType();

            BigDecimal newBalance = ConsoleUtil.promptOptionalBigDecimal("New Balance", existing.getBalance());

            existing.setAccountName(newName);
            existing.setAccountType(newType);
            existing.setBalance(newBalance);

            boolean ok = accountDAO.updateAccount(existing);
            if (ok) {
                ConsoleUtil.printSuccess("Account #" + accountId + " updated successfully!");
            } else {
                ConsoleUtil.printError("Failed to update account.");
            }
        } catch (SQLException e) {
            ConsoleUtil.printError("Database error during update: " + e.getMessage());
        }
        ConsoleUtil.pause();
    }

    private void deleteAccount() {
        ConsoleUtil.printSubHeader("DELETE ACCOUNT");
        listAccounts();

        int accountId = ConsoleUtil.promptInt("Enter Account ID to delete (or 0 to cancel)", 0, Integer.MAX_VALUE);
        if (accountId == 0) return;

        try {
            Account existing = accountDAO.findById(accountId);
            if (existing == null || !existing.getUserId().equals(session.getUserId())) {
                ConsoleUtil.printError("Account #" + accountId + " not found or does not belong to you.");
                ConsoleUtil.pause();
                return;
            }

            boolean isDefault = session.getDefaultAccountId() != null && session.getDefaultAccountId().equals(accountId);
            if (isDefault) {
                ConsoleUtil.printWarning("This account is currently your primary default account.");
            }
            ConsoleUtil.printWarning("Deleting an account will cascade delete its associated transactions and goals!");

            boolean confirm = ConsoleUtil.promptConfirm("Are you sure you want to permanently delete '" + existing.getAccountName() + "'?");
            if (!confirm) {
                ConsoleUtil.printInfo("Deletion cancelled.");
                ConsoleUtil.pause();
                return;
            }

            boolean ok = accountDAO.deleteAccount(accountId);
            if (ok) {
                session.refreshUser(userDAO);
                ConsoleUtil.printSuccess("Account #" + accountId + " was deleted.");
            } else {
                ConsoleUtil.printError("Failed to delete account.");
            }
        } catch (SQLException e) {
            ConsoleUtil.printError("Could not delete account: " + e.getMessage());
        }
        ConsoleUtil.pause();
    }

    private void setDefaultAccount() {
        ConsoleUtil.printSubHeader("DESIGNATE DEFAULT ACCOUNT");
        listAccounts();

        int accountId = ConsoleUtil.promptInt("Enter Account ID to set as default (or 0 to cancel)", 0, Integer.MAX_VALUE);
        if (accountId == 0) return;

        try {
            Account existing = accountDAO.findById(accountId);
            if (existing == null || !existing.getUserId().equals(session.getUserId())) {
                ConsoleUtil.printError("Account #" + accountId + " not found or does not belong to you.");
                ConsoleUtil.pause();
                return;
            }

            boolean ok = accountDAO.setDefaultAccount(session.getUserId(), accountId);
            if (ok) {
                session.refreshUser(userDAO);
                ConsoleUtil.printSuccess("Account #" + accountId + " ('" + existing.getAccountName() + "') is now your default account!");
            } else {
                ConsoleUtil.printError("Failed to designate default account.");
            }
        } catch (SQLException e) {
            ConsoleUtil.printError("Database error setting default account: " + e.getMessage());
        }
        ConsoleUtil.pause();
    }
}
