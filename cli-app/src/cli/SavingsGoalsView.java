package cli;

import dao.AccountDAO;
import dao.SavingsGoalDAO;
import model.Account;
import model.SavingsGoal;
import model.SavingsGoalStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Savings Goals management view for FinTrack CLI.
 * Handles tracking financial ambitions, target milestones,
 * progress percentages, deposit updates, and goal completion.
 */
public class SavingsGoalsView {

    private final SessionState session;
    private final SavingsGoalDAO savingsGoalDAO;
    private final AccountDAO accountDAO;

    public SavingsGoalsView(SessionState session, SavingsGoalDAO savingsGoalDAO, AccountDAO accountDAO) {
        this.session = session;
        this.savingsGoalDAO = savingsGoalDAO;
        this.accountDAO = accountDAO;
    }

    public void run() {
        while (true) {
            ConsoleUtil.printSubHeader("SAVINGS GOALS MANAGEMENT");
            System.out.println("1. List All Savings Goals");
            System.out.println("2. Create New Savings Goal");
            System.out.println("3. Update Goal Details");
            System.out.println("4. Update Saved Amount / Deposit");
            System.out.println("5. Delete Savings Goal");
            System.out.println("6. Return to Main Menu");

            int choice = ConsoleUtil.promptInt("Enter choice", 1, 6);
            switch (choice) {
                case 1:
                    listGoals();
                    ConsoleUtil.pause();
                    break;
                case 2:
                    createGoal();
                    break;
                case 3:
                    updateGoal();
                    break;
                case 4:
                    updateSavedAmount();
                    break;
                case 5:
                    deleteGoal();
                    break;
                case 6:
                    return;
            }
        }
    }

    private void listGoals() {
        try {
            List<SavingsGoal> goals = savingsGoalDAO.getGoalsByUserId(session.getUserId());
            ConsoleUtil.printSubHeader("YOUR SAVINGS GOALS (" + goals.size() + ")");

            if (goals.isEmpty()) {
                ConsoleUtil.printInfo("No savings goals set yet. Define your first goal using option 2.");
                return;
            }

            Map<Integer, String> accountNames = new HashMap<>();
            for (Account a : accountDAO.getAccountsByUserId(session.getUserId())) {
                accountNames.put(a.getAccountId(), a.getAccountName());
            }

            System.out.println(String.format(" %-5s | %-20s | %-13s | %-13s | %-8s | %-13s | %-11s | %-10s | %s",
                    "ID", "Goal Name", "Target", "Saved", "Progress", "Remaining", "Deadline", "Status", "Linked Account"));
            ConsoleUtil.printDivider();

            for (SavingsGoal g : goals) {
                BigDecimal target = g.getTargetAmount() != null ? g.getTargetAmount() : BigDecimal.ZERO;
                BigDecimal saved = g.getSavedAmount() != null ? g.getSavedAmount() : BigDecimal.ZERO;
                BigDecimal remaining = target.subtract(saved);
                if (remaining.compareTo(BigDecimal.ZERO) < 0) {
                    remaining = BigDecimal.ZERO;
                }

                double pct = 0.0;
                if (target.compareTo(BigDecimal.ZERO) > 0) {
                    pct = saved.divide(target, 4, RoundingMode.HALF_UP).doubleValue() * 100.0;
                    if (pct > 100.0) pct = 100.0;
                }

                String accName = g.getAccountId() != null ? accountNames.getOrDefault(g.getAccountId(), "#" + g.getAccountId()) : "None";

                System.out.println(String.format(" #%-4d | %-20s | %13s | %13s | %6.1f%% | %13s | %-11s | %-10s | %s",
                        g.getGoalId(),
                        ConsoleUtil.padRight(g.getGoalName(), 20),
                        ConsoleUtil.formatCurrency(target),
                        ConsoleUtil.formatCurrency(saved),
                        pct,
                        ConsoleUtil.formatCurrency(remaining),
                        ConsoleUtil.formatDate(g.getTargetDate()),
                        g.getStatus(),
                        accName));
            }

        } catch (SQLException e) {
            ConsoleUtil.printError("Failed to fetch goals: " + e.getMessage());
        }
    }

    private void createGoal() {
        ConsoleUtil.printSubHeader("CREATE SAVINGS GOAL");
        String name = ConsoleUtil.promptString("Goal Name (e.g. Vacation Trip, Emergency Fund)");
        BigDecimal target = ConsoleUtil.promptBigDecimal("Target Amount (₹)", false, true);

        // Optional initial saved amount
        BigDecimal initialSaved = ConsoleUtil.promptOptionalBigDecimal("Initial Saved Amount", BigDecimal.ZERO);
        if (initialSaved == null) initialSaved = BigDecimal.ZERO;

        // Optional deadline
        LocalDate targetDate = ConsoleUtil.promptLocalDate("Target Date", true, null);

        // Optional linked account
        Integer linkedAccountId = null;
        try {
            List<Account> accounts = accountDAO.getAccountsByUserId(session.getUserId());
            if (!accounts.isEmpty()) {
                System.out.println("\nOptional: Link a funding account (0 for None):");
                for (int i = 0; i < accounts.size(); i++) {
                    Account a = accounts.get(i);
                    System.out.println((i + 1) + ". #" + a.getAccountId() + " " + a.getAccountName());
                }
                int accIdx = ConsoleUtil.promptInt("Choice", 0, accounts.size());
                if (accIdx > 0) {
                    linkedAccountId = accounts.get(accIdx - 1).getAccountId();
                }
            }
        } catch (SQLException ignored) {
        }

        SavingsGoal goal = new SavingsGoal();
        goal.setUserId(session.getUserId());
        goal.setGoalName(name);
        goal.setTargetAmount(target);
        goal.setSavedAmount(initialSaved);
        goal.setTargetDate(targetDate);
        goal.setAccountId(linkedAccountId);
        goal.setStatus(SavingsGoalStatus.Active);

        try {
            SavingsGoal created = savingsGoalDAO.createSavingsGoal(goal);
            ConsoleUtil.printSuccess("Savings goal '" + created.getGoalName() + "' created with ID #" + created.getGoalId() + "!");
        } catch (SQLException e) {
            ConsoleUtil.printError("Could not create savings goal: " + e.getMessage());
        }
        ConsoleUtil.pause();
    }

    private void updateGoal() {
        ConsoleUtil.printSubHeader("UPDATE SAVINGS GOAL");
        listGoals();

        int goalId = ConsoleUtil.promptInt("Enter Goal ID to update (or 0 to cancel)", 0, Integer.MAX_VALUE);
        if (goalId == 0) return;

        try {
            SavingsGoal existing = savingsGoalDAO.findById(session.getUserId(), goalId);
            if (existing == null) {
                ConsoleUtil.printError("Savings goal #" + goalId + " not found or access denied.");
                ConsoleUtil.pause();
                return;
            }

            System.out.println("\nEditing Goal: " + existing.getGoalName());
            String newName = ConsoleUtil.promptOptionalString("New Goal Name", existing.getGoalName());
            BigDecimal newTarget = ConsoleUtil.promptOptionalBigDecimal("New Target Amount", existing.getTargetAmount());
            LocalDate newDate = ConsoleUtil.promptLocalDate("New Target Date", true, existing.getTargetDate());

            System.out.println("\nStatus: 1. Keep " + existing.getStatus() + " | 2. Active | 3. Completed | 4. Cancelled");
            int sChoice = ConsoleUtil.promptInt("Choice", 1, 4);
            SavingsGoalStatus newStatus = existing.getStatus();
            if (sChoice == 2) newStatus = SavingsGoalStatus.Active;
            if (sChoice == 3) newStatus = SavingsGoalStatus.Completed;
            if (sChoice == 4) newStatus = SavingsGoalStatus.Cancelled;

            // Optional update linked account
            List<Account> accounts = accountDAO.getAccountsByUserId(session.getUserId());
            Integer newAccId = existing.getAccountId();
            if (!accounts.isEmpty()) {
                System.out.println("\nLinked Account (0 to leave unchanged, -1 to unlink):");
                for (int i = 0; i < accounts.size(); i++) {
                    Account a = accounts.get(i);
                    String cur = (existing.getAccountId() != null && existing.getAccountId().equals(a.getAccountId())) ? " [CURRENT]" : "";
                    System.out.println((i + 1) + ". #" + a.getAccountId() + " " + a.getAccountName() + cur);
                }
                int accIdx = ConsoleUtil.promptInt("Choice", -1, accounts.size());
                if (accIdx > 0) {
                    newAccId = accounts.get(accIdx - 1).getAccountId();
                } else if (accIdx == -1) {
                    newAccId = null;
                }
            }

            existing.setGoalName(newName);
            existing.setTargetAmount(newTarget);
            existing.setTargetDate(newDate);
            existing.setStatus(newStatus);
            existing.setAccountId(newAccId);

            boolean ok = savingsGoalDAO.updateSavingsGoal(existing);
            if (ok) {
                ConsoleUtil.printSuccess("Savings goal #" + goalId + " updated successfully!");
            } else {
                ConsoleUtil.printError("Failed to update savings goal.");
            }

        } catch (SQLException e) {
            ConsoleUtil.printError("Database error during update: " + e.getMessage());
        }
        ConsoleUtil.pause();
    }

    private void updateSavedAmount() {
        ConsoleUtil.printSubHeader("UPDATE SAVED AMOUNT / DEPOSIT");
        listGoals();

        int goalId = ConsoleUtil.promptInt("Enter Goal ID to update (or 0 to cancel)", 0, Integer.MAX_VALUE);
        if (goalId == 0) return;

        try {
            SavingsGoal existing = savingsGoalDAO.findById(session.getUserId(), goalId);
            if (existing == null) {
                ConsoleUtil.printError("Savings goal #" + goalId + " not found or access denied.");
                ConsoleUtil.pause();
                return;
            }

            BigDecimal currentSaved = existing.getSavedAmount() != null ? existing.getSavedAmount() : BigDecimal.ZERO;
            BigDecimal target = existing.getTargetAmount() != null ? existing.getTargetAmount() : BigDecimal.ZERO;

            System.out.println("\nGoal: " + existing.getGoalName());
            System.out.println("  Current Saved: " + ConsoleUtil.formatCurrency(currentSaved));
            System.out.println("  Target Amount: " + ConsoleUtil.formatCurrency(target));

            System.out.println("\nUpdate Option:");
            System.out.println("1. Add / Deposit funds (e.g. saved + deposit)");
            System.out.println("2. Directly overwrite total saved amount");
            int opt = ConsoleUtil.promptInt("Choice", 1, 2);

            BigDecimal newSaved;
            if (opt == 1) {
                BigDecimal deposit = ConsoleUtil.promptBigDecimal("Deposit Amount (₹)", false, true);
                newSaved = currentSaved.add(deposit);
            } else {
                newSaved = ConsoleUtil.promptBigDecimal("New Total Saved Amount (₹)", true, true);
            }

            existing.setSavedAmount(newSaved);

            // If goal achieved, offer to mark completed
            if (newSaved.compareTo(target) >= 0 && existing.getStatus() == SavingsGoalStatus.Active) {
                ConsoleUtil.printSuccess("Target reached! (" + ConsoleUtil.formatCurrency(newSaved) + " >= " + ConsoleUtil.formatCurrency(target) + ")");
                boolean markCompleted = ConsoleUtil.promptConfirm("Would you like to mark this goal as Completed?");
                if (markCompleted) {
                    existing.setStatus(SavingsGoalStatus.Completed);
                }
            }

            boolean ok = savingsGoalDAO.updateSavingsGoal(existing);
            if (ok) {
                ConsoleUtil.printSuccess("Saved amount for '" + existing.getGoalName() + "' updated to " + ConsoleUtil.formatCurrency(newSaved) + "!");
            } else {
                ConsoleUtil.printError("Failed to update saved balance.");
            }

        } catch (SQLException e) {
            ConsoleUtil.printError("Database error during update: " + e.getMessage());
        }
        ConsoleUtil.pause();
    }

    private void deleteGoal() {
        ConsoleUtil.printSubHeader("DELETE SAVINGS GOAL");
        listGoals();

        int goalId = ConsoleUtil.promptInt("Enter Goal ID to delete (or 0 to cancel)", 0, Integer.MAX_VALUE);
        if (goalId == 0) return;

        try {
            SavingsGoal existing = savingsGoalDAO.findById(session.getUserId(), goalId);
            if (existing == null) {
                ConsoleUtil.printError("Savings goal #" + goalId + " not found or access denied.");
                ConsoleUtil.pause();
                return;
            }

            boolean confirm = ConsoleUtil.promptConfirm("Are you sure you want to delete goal '" + existing.getGoalName() + "'?");
            if (!confirm) {
                ConsoleUtil.printInfo("Deletion cancelled.");
                ConsoleUtil.pause();
                return;
            }

            boolean ok = savingsGoalDAO.deleteSavingsGoal(session.getUserId(), goalId);
            if (ok) {
                ConsoleUtil.printSuccess("Savings goal #" + goalId + " was deleted.");
            } else {
                ConsoleUtil.printError("Failed to delete savings goal.");
            }

        } catch (SQLException e) {
            ConsoleUtil.printError("Database error during deletion: " + e.getMessage());
        }
        ConsoleUtil.pause();
    }
}
