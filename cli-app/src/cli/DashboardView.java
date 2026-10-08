package cli;

import dao.AccountDAO;
import dao.SavingsGoalDAO;
import dao.TransactionDAO;
import model.Account;
import model.SavingsGoal;
import model.SavingsGoalStatus;
import model.Transaction;
import model.TransactionType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Dashboard View for the FinTrack CLI.
 * Aggregates real-time financial metrics for the authenticated user:
 * total balance, income, expenses, net savings, active goals, and recent activity.
 * Supports exporting summary reports to cli-app/output/.
 */
public class DashboardView {

    private final SessionState session;
    private final AccountDAO accountDAO;
    private final TransactionDAO transactionDAO;
    private final SavingsGoalDAO savingsGoalDAO;

    public DashboardView(SessionState session, AccountDAO accountDAO, TransactionDAO transactionDAO, SavingsGoalDAO savingsGoalDAO) {
        this.session = session;
        this.accountDAO = accountDAO;
        this.transactionDAO = transactionDAO;
        this.savingsGoalDAO = savingsGoalDAO;
    }

    public void show() {
        int userId = session.getUserId();

        ConsoleUtil.printHeader("FINANCIAL DASHBOARD");
        System.out.println("User:  " + session.getUserName() + " (" + session.getEmail() + ")");
        System.out.println("Time:  " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        ConsoleUtil.printDivider();

        try {
            // 1. Accounts & Total Balance
            List<Account> accounts = accountDAO.getAccountsByUserId(userId);
            BigDecimal totalBalance = BigDecimal.ZERO;
            for (Account acc : accounts) {
                if (acc.getBalance() != null) {
                    totalBalance = totalBalance.add(acc.getBalance());
                }
            }

            // 2. Transactions, Income, Expenses
            List<Transaction> transactions = transactionDAO.getTransactionsByUserId(userId);
            BigDecimal totalIncome = BigDecimal.ZERO;
            BigDecimal totalExpenses = BigDecimal.ZERO;

            for (Transaction tx : transactions) {
                if (tx.getAmount() == null) continue;
                if (tx.getTransactionType() == TransactionType.Income) {
                    totalIncome = totalIncome.add(tx.getAmount());
                } else if (tx.getTransactionType() == TransactionType.Expense) {
                    totalExpenses = totalExpenses.add(tx.getAmount());
                }
            }
            BigDecimal netIncome = totalIncome.subtract(totalExpenses);

            // 3. Savings Goals
            List<SavingsGoal> goals = savingsGoalDAO.getGoalsByUserId(userId);
            int activeGoalsCount = 0;
            BigDecimal totalSavedInGoals = BigDecimal.ZERO;
            BigDecimal totalTargetGoals = BigDecimal.ZERO;

            for (SavingsGoal g : goals) {
                if (g.getStatus() == SavingsGoalStatus.Active) {
                    activeGoalsCount++;
                }
                if (g.getSavedAmount() != null) {
                    totalSavedInGoals = totalSavedInGoals.add(g.getSavedAmount());
                }
                if (g.getTargetAmount() != null) {
                    totalTargetGoals = totalTargetGoals.add(g.getTargetAmount());
                }
            }

            // Display Summary Cards
            System.out.println(String.format("  %-25s : %s", "Total Net Worth (Accounts)", ConsoleUtil.formatCurrency(totalBalance)));
            System.out.println(String.format("  %-25s : %s", "Total Logged Income", ConsoleUtil.formatCurrency(totalIncome)));
            System.out.println(String.format("  %-25s : %s", "Total Logged Expenses", ConsoleUtil.formatCurrency(totalExpenses)));
            System.out.println(String.format("  %-25s : %s", "Net Savings Balance", ConsoleUtil.formatCurrency(netIncome)));
            System.out.println(String.format("  %-25s : %d (Total Target: %s)", "Active Savings Goals", activeGoalsCount, ConsoleUtil.formatCurrency(totalTargetGoals)));
            ConsoleUtil.printDivider();

            // Account Snapshots
            System.out.println("YOUR ACCOUNTS (" + accounts.size() + "):");
            if (accounts.isEmpty()) {
                System.out.println("  No accounts found. Use menu option 2 to create an account.");
            } else {
                for (Account acc : accounts) {
                    boolean isDefault = session.getDefaultAccountId() != null && session.getDefaultAccountId().equals(acc.getAccountId());
                    String badge = isDefault ? " [DEFAULT]" : "";
                    System.out.println(String.format("  - #%-3d %-22s (%-12s) : %14s%s",
                            acc.getAccountId(),
                            acc.getAccountName(),
                            acc.getAccountType(),
                            ConsoleUtil.formatCurrency(acc.getBalance()),
                            badge));
                }
            }
            ConsoleUtil.printDivider();

            // Recent Activity (Top 5)
            System.out.println("RECENT TRANSACTIONS (Latest " + Math.min(5, transactions.size()) + "):");
            if (transactions.isEmpty()) {
                System.out.println("  No transactions recorded yet.");
            } else {
                int limit = Math.min(5, transactions.size());
                for (int i = 0; i < limit; i++) {
                    Transaction tx = transactions.get(i);
                    String sign = tx.getTransactionType() == TransactionType.Income ? "+" : "-";
                    System.out.println(String.format("  - %s | #%-4d | %-7s | %s%s | %s",
                            ConsoleUtil.formatDate(tx.getTransactionDate()),
                            tx.getTransactionId(),
                            tx.getTransactionType(),
                            sign,
                            ConsoleUtil.formatCurrency(tx.getAmount()),
                            tx.getDescription() != null ? tx.getDescription() : ""));
                }
            }
            ConsoleUtil.printDivider();

            // Options
            System.out.println("Options:");
            System.out.println("1. Export Financial Summary to output/");
            System.out.println("2. Return to Main Menu");
            int choice = ConsoleUtil.promptInt("Enter choice", 1, 2);

            if (choice == 1) {
                exportSummaryReport(accounts, totalBalance, totalIncome, totalExpenses, netIncome, goals);
            }

        } catch (SQLException e) {
            ConsoleUtil.printError("Failed to load dashboard data: " + e.getMessage());
            ConsoleUtil.pause();
        }
    }

    private void exportSummaryReport(List<Account> accounts, BigDecimal totalBalance,
                                     BigDecimal totalIncome, BigDecimal totalExpenses,
                                     BigDecimal netIncome, List<SavingsGoal> goals) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File outputDir = new File("cli-app/output");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        File outFile = new File(outputDir, "summary_user" + session.getUserId() + "_" + timestamp + ".txt");

        try (FileWriter writer = new FileWriter(outFile)) {
            writer.write("================================================================================\n");
            writer.write("  FINTRACK FINANCIAL SUMMARY REPORT\n");
            writer.write("================================================================================\n");
            writer.write("User:       " + session.getUserName() + " (" + session.getEmail() + ")\n");
            writer.write("Generated:  " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            writer.write("--------------------------------------------------------------------------------\n");
            writer.write(String.format("Total Account Balance : %s\n", ConsoleUtil.formatCurrency(totalBalance)));
            writer.write(String.format("Total Income          : %s\n", ConsoleUtil.formatCurrency(totalIncome)));
            writer.write(String.format("Total Expenses        : %s\n", ConsoleUtil.formatCurrency(totalExpenses)));
            writer.write(String.format("Net Income (Savings)  : %s\n", ConsoleUtil.formatCurrency(netIncome)));
            writer.write("--------------------------------------------------------------------------------\n\n");

            writer.write("ACCOUNTS SUMMARY:\n");
            for (Account acc : accounts) {
                boolean isDefault = session.getDefaultAccountId() != null && session.getDefaultAccountId().equals(acc.getAccountId());
                writer.write(String.format("  #%-3d %-22s (%-12s) : %s%s\n",
                        acc.getAccountId(),
                        acc.getAccountName(),
                        acc.getAccountType(),
                        ConsoleUtil.formatCurrency(acc.getBalance()),
                        isDefault ? " [DEFAULT]" : ""));
            }
            writer.write("\nSAVINGS GOALS:\n");
            for (SavingsGoal g : goals) {
                writer.write(String.format("  #%-3d %-22s : Saved %s / Target %s (%s)\n",
                        g.getGoalId(),
                        g.getGoalName(),
                        ConsoleUtil.formatCurrency(g.getSavedAmount()),
                        ConsoleUtil.formatCurrency(g.getTargetAmount()),
                        g.getStatus()));
            }
            writer.write("\n================================================================================\n");

            ConsoleUtil.printSuccess("Summary report exported successfully to: " + outFile.getPath());
        } catch (IOException e) {
            ConsoleUtil.printError("Could not write export file: " + e.getMessage());
        }
        ConsoleUtil.pause();
    }
}
