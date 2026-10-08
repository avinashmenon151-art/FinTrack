package cli;

import dao.AccountDAO;
import dao.BudgetCategoryDAO;
import dao.BudgetDAO;
import dao.CategoryDAO;
import dao.SavingsGoalDAO;
import dao.TransactionDAO;
import dao.UserDAO;
import model.User;
import org.mindrot.jbcrypt.BCrypt;
import util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Main entry point for the FinTrack Command-Line Interface (CLI).
 * <p>
 * Provides terminal-based interaction for FinTrack while sharing the existing
 * PostgreSQL database and backend DAO/model layer.
 */
public class FinTrackCli {

    private static final SessionState session = SessionState.getInstance();

    private static final UserDAO userDAO = new UserDAO();
    private static final AccountDAO accountDAO = new AccountDAO();
    private static final TransactionDAO transactionDAO = new TransactionDAO();
    private static final CategoryDAO categoryDAO = new CategoryDAO();
    private static final BudgetDAO budgetDAO = new BudgetDAO();
    private static final BudgetCategoryDAO budgetCategoryDAO = new BudgetCategoryDAO();
    private static final SavingsGoalDAO savingsGoalDAO = new SavingsGoalDAO();

    public static void main(String[] args) {
        printBanner();

        // 1. Check database connectivity
        if (!testDatabaseConnection()) {
            System.out.println();
            ConsoleUtil.printError("Could not establish a connection to the FinTrack PostgreSQL database.");
            System.out.println("Please verify that:");
            System.out.println("  1. PostgreSQL service is running on localhost:5432.");
            System.out.println("  2. Database 'fintrack' exists and migrations/seeds have been applied.");
            System.out.println("  3. Environment variables DB_URL, DB_USER, and DB_PASSWORD are set correctly if non-default.");
            System.out.println("     Example (PowerShell): $env:DB_PASSWORD=\"your_password\"");
            System.out.println();
            System.out.println("Exiting FinTrack CLI.");
            System.exit(1);
            return;
        }

        // 2. Application loop
        boolean running = true;
        while (running) {
            if (!session.isLoggedIn()) {
                boolean stayInApp = runLoginFlow();
                if (!stayInApp) {
                    running = false;
                    break;
                }
            } else {
                running = runMainMenu();
            }
        }

        printGoodbye();
    }

    private static void printBanner() {
        System.out.println();
        System.out.println("================================================================");
        System.out.println("                 FINTRACK - FINANCIAL TRACKER                   ");
        System.out.println("                  Command-Line Interface (CLI)                  ");
        System.out.println("================================================================");
        System.out.println("  Secure personal finance management for your terminal.");
        System.out.println("  Connected to shared FinTrack PostgreSQL database.");
        System.out.println("================================================================");
        System.out.println();
    }

    private static boolean testDatabaseConnection() {
        System.out.print("Connecting to FinTrack database... ");
        try (Connection conn = DBConnection.getConnection()) {
            if (conn != null && !conn.isClosed()) {
                System.out.println("[CONNECTED]");
                return true;
            }
        } catch (SQLException e) {
            System.out.println("[FAILED]");
            System.out.println("Error details: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("[FAILED]");
            System.out.println("Error details: " + e.getMessage());
        }
        return false;
    }

    private static boolean runLoginFlow() {
        while (!session.isLoggedIn()) {
            ConsoleUtil.printHeader("USER LOGIN");
            System.out.println("Enter your credentials to access FinTrack, or enter 0 at email to exit.");
            System.out.println();

            String email = ConsoleUtil.promptString("Email address");
            if ("0".equals(email) || "exit".equalsIgnoreCase(email) || "quit".equalsIgnoreCase(email)) {
                return false;
            }

            if (!email.contains("@") || !email.contains(".")) {
                ConsoleUtil.printError("Invalid email format. Please try again.");
                continue;
            }

            String password = ConsoleUtil.promptPassword("Password");
            if (password == null || password.trim().isEmpty()) {
                ConsoleUtil.printError("Password cannot be empty.");
                continue;
            }

            try {
                User user = userDAO.findByEmail(email.trim().toLowerCase());
                if (user == null || user.getPasswordHash() == null) {
                    ConsoleUtil.printError("Invalid email or password.");
                    continue;
                }

                boolean match = BCrypt.checkpw(password, user.getPasswordHash());
                if (!match) {
                    ConsoleUtil.printError("Invalid email or password.");
                    continue;
                }

                // Login successful
                session.setUser(user);
                ConsoleUtil.printSuccess("Welcome back, " + user.getName() + "!");
                System.out.println("Signed in as: " + user.getEmail() + " (User ID: " + user.getUserId() + ")");
                return true;

            } catch (SQLException e) {
                ConsoleUtil.printError("Database error during authentication: " + e.getMessage());
            } catch (Exception e) {
                ConsoleUtil.printError("Authentication failed due to an unexpected error.");
            }
        }
        return true;
    }

    private static boolean runMainMenu() {
        User user = session.getUser();
        String name = (user != null && user.getName() != null) ? user.getName() : "User";

        ConsoleUtil.printHeader("FINTRACK MAIN MENU - " + name.toUpperCase());
        System.out.println(" 1. Dashboard");
        System.out.println(" 2. Accounts");
        System.out.println(" 3. Transactions");
        System.out.println(" 4. Categories");
        System.out.println(" 5. Budgets");
        System.out.println(" 6. Savings Goals");
        System.out.println(" 7. Logout");
        System.out.println(" 8. Exit");
        ConsoleUtil.printDivider();

        int choice = ConsoleUtil.promptInt("Select an option (1-8)", 1, 8);

        switch (choice) {
            case 1: {
                DashboardView dashboard = new DashboardView(session, accountDAO, transactionDAO, savingsGoalDAO);
                dashboard.show();
                break;
            }
            case 2: {
                AccountsView accounts = new AccountsView(session, accountDAO, userDAO);
                accounts.run();
                break;
            }
            case 3: {
                TransactionsView transactions = new TransactionsView(session, transactionDAO, accountDAO, categoryDAO);
                transactions.run();
                break;
            }
            case 4: {
                CategoriesView categories = new CategoriesView(session, categoryDAO);
                categories.run();
                break;
            }
            case 5: {
                BudgetsView budgets = new BudgetsView(session, budgetDAO, budgetCategoryDAO, categoryDAO);
                budgets.run();
                break;
            }
            case 6: {
                SavingsGoalsView savingsGoals = new SavingsGoalsView(session, savingsGoalDAO, accountDAO);
                savingsGoals.run();
                break;
            }
            case 7:
                handleLogout();
                break;
            case 8:
                return false; // Exit app
            default:
                ConsoleUtil.printError("Invalid choice.");
                break;
        }

        return true;
    }

    private static void handleLogout() {
        User user = session.getUser();
        String name = (user != null) ? user.getName() : "User";
        session.clear();
        ConsoleUtil.printSuccess("You have been logged out successfully. Goodbye, " + name + "!");
    }

    private static void printGoodbye() {
        System.out.println();
        System.out.println("================================================================");
        System.out.println("         Thank you for using FinTrack CLI. Goodbye!             ");
        System.out.println("================================================================");
        System.out.println();
    }
}
