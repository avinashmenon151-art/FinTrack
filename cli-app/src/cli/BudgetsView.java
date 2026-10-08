package cli;

import dao.BudgetCategoryDAO;
import dao.BudgetDAO;
import dao.CategoryDAO;
import model.Budget;
import model.BudgetCategory;
import model.Category;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Budget management view for FinTrack CLI.
 * Handles planning budgets, configuring date horizons,
 * and assigning category-specific fund allocations.
 * Strictly enforces user ownership and date ordering.
 */
public class BudgetsView {

    private final SessionState session;
    private final BudgetDAO budgetDAO;
    private final BudgetCategoryDAO budgetCategoryDAO;
    private final CategoryDAO categoryDAO;

    public BudgetsView(SessionState session, BudgetDAO budgetDAO, BudgetCategoryDAO budgetCategoryDAO, CategoryDAO categoryDAO) {
        this.session = session;
        this.budgetDAO = budgetDAO;
        this.budgetCategoryDAO = budgetCategoryDAO;
        this.categoryDAO = categoryDAO;
    }

    public void run() {
        while (true) {
            ConsoleUtil.printSubHeader("BUDGET MANAGEMENT");
            System.out.println("1. List All Budgets");
            System.out.println("2. Create New Budget");
            System.out.println("3. Update Budget Dates / Name");
            System.out.println("4. Delete Budget");
            System.out.println("5. Manage Category Allocations for a Budget");
            System.out.println("6. Return to Main Menu");

            int choice = ConsoleUtil.promptInt("Enter choice", 1, 6);
            switch (choice) {
                case 1:
                    listBudgets();
                    ConsoleUtil.pause();
                    break;
                case 2:
                    createBudget();
                    break;
                case 3:
                    updateBudget();
                    break;
                case 4:
                    deleteBudget();
                    break;
                case 5:
                    manageAllocations();
                    break;
                case 6:
                    return;
            }
        }
    }

    private void listBudgets() {
        try {
            List<Budget> budgets = budgetDAO.getBudgetsByUserId(session.getUserId());
            ConsoleUtil.printSubHeader("YOUR BUDGETS (" + budgets.size() + ")");
            if (budgets.isEmpty()) {
                ConsoleUtil.printInfo("No budgets found. Plan your first budget using option 2.");
                return;
            }

            System.out.println(String.format(" %-5s | %-24s | %-12s | %-12s | %-16s | %s",
                    "ID", "Budget Name", "Start Date", "End Date", "Allocated Sum", "Categories"));
            ConsoleUtil.printDivider();

            for (Budget b : budgets) {
                List<BudgetCategory> allocations = budgetCategoryDAO.getCategoriesForBudget(b.getBudgetId());
                BigDecimal sum = BigDecimal.ZERO;
                for (BudgetCategory bc : allocations) {
                    if (bc.getAllocatedAmount() != null) {
                        sum = sum.add(bc.getAllocatedAmount());
                    }
                }

                System.out.println(String.format(" #%-4d | %-24s | %-12s | %-12s | %16s | %d mapped",
                        b.getBudgetId(),
                        b.getBudgetName(),
                        ConsoleUtil.formatDate(b.getStartDate()),
                        ConsoleUtil.formatDate(b.getEndDate()),
                        ConsoleUtil.formatCurrency(sum),
                        allocations.size()));
            }
        } catch (SQLException e) {
            ConsoleUtil.printError("Failed to fetch budgets: " + e.getMessage());
        }
    }

    private void createBudget() {
        ConsoleUtil.printSubHeader("CREATE NEW BUDGET");
        String name = ConsoleUtil.promptString("Budget Name (e.g. October 2026 Household Budget)");

        LocalDate startDate = ConsoleUtil.promptLocalDate("Start Date", false, LocalDate.now().withDayOfMonth(1));
        LocalDate endDate;

        while (true) {
            endDate = ConsoleUtil.promptLocalDate("End Date", false, startDate.plusMonths(1).minusDays(1));
            if (!endDate.isBefore(startDate)) {
                break;
            }
            ConsoleUtil.printError("End date must be greater than or equal to start date (" + ConsoleUtil.formatDate(startDate) + ").");
        }

        Budget budget = new Budget();
        budget.setUserId(session.getUserId());
        budget.setBudgetName(name);
        budget.setStartDate(startDate);
        budget.setEndDate(endDate);

        try {
            Budget created = budgetDAO.createBudget(budget);
            ConsoleUtil.printSuccess("Budget '" + created.getBudgetName() + "' created with ID #" + created.getBudgetId() + "!");
        } catch (SQLException e) {
            ConsoleUtil.printError("Could not create budget: " + e.getMessage());
        }
        ConsoleUtil.pause();
    }

    private void updateBudget() {
        ConsoleUtil.printSubHeader("UPDATE BUDGET");
        listBudgets();

        int budgetId = ConsoleUtil.promptInt("Enter Budget ID to update (or 0 to cancel)", 0, Integer.MAX_VALUE);
        if (budgetId == 0) return;

        try {
            Budget existing = budgetDAO.findById(session.getUserId(), budgetId);
            if (existing == null) {
                ConsoleUtil.printError("Budget #" + budgetId + " not found or does not belong to you.");
                ConsoleUtil.pause();
                return;
            }

            System.out.println("\nEditing: " + existing.getBudgetName());
            String newName = ConsoleUtil.promptOptionalString("New Budget Name", existing.getBudgetName());
            LocalDate newStart = ConsoleUtil.promptLocalDate("New Start Date", true, existing.getStartDate());

            LocalDate newEnd;
            while (true) {
                newEnd = ConsoleUtil.promptLocalDate("New End Date", true, existing.getEndDate());
                if (!newEnd.isBefore(newStart)) {
                    break;
                }
                ConsoleUtil.printError("End date cannot be prior to start date (" + ConsoleUtil.formatDate(newStart) + ").");
            }

            existing.setBudgetName(newName);
            existing.setStartDate(newStart);
            existing.setEndDate(newEnd);

            boolean ok = budgetDAO.updateBudget(existing);
            if (ok) {
                ConsoleUtil.printSuccess("Budget #" + budgetId + " updated successfully!");
            } else {
                ConsoleUtil.printError("Failed to update budget.");
            }

        } catch (SQLException e) {
            ConsoleUtil.printError("Database error during update: " + e.getMessage());
        }
        ConsoleUtil.pause();
    }

    private void deleteBudget() {
        ConsoleUtil.printSubHeader("DELETE BUDGET");
        listBudgets();

        int budgetId = ConsoleUtil.promptInt("Enter Budget ID to delete (or 0 to cancel)", 0, Integer.MAX_VALUE);
        if (budgetId == 0) return;

        try {
            Budget existing = budgetDAO.findById(session.getUserId(), budgetId);
            if (existing == null) {
                ConsoleUtil.printError("Budget #" + budgetId + " not found or does not belong to you.");
                ConsoleUtil.pause();
                return;
            }

            boolean confirm = ConsoleUtil.promptConfirm("Are you sure you want to delete budget '" + existing.getBudgetName() + "' and all its category allocations?");
            if (!confirm) {
                ConsoleUtil.printInfo("Deletion cancelled.");
                ConsoleUtil.pause();
                return;
            }

            boolean ok = budgetDAO.deleteBudget(session.getUserId(), budgetId);
            if (ok) {
                ConsoleUtil.printSuccess("Budget #" + budgetId + " was deleted.");
            } else {
                ConsoleUtil.printError("Failed to delete budget.");
            }

        } catch (SQLException e) {
            ConsoleUtil.printError("Database error during deletion: " + e.getMessage());
        }
        ConsoleUtil.pause();
    }

    private void manageAllocations() {
        ConsoleUtil.printSubHeader("MANAGE BUDGET CATEGORY ALLOCATIONS");
        listBudgets();

        int budgetId = ConsoleUtil.promptInt("Enter Budget ID to manage allocations for (or 0 to return)", 0, Integer.MAX_VALUE);
        if (budgetId == 0) return;

        try {
            Budget budget = budgetDAO.findById(session.getUserId(), budgetId);
            if (budget == null) {
                ConsoleUtil.printError("Budget #" + budgetId + " not found or access denied.");
                ConsoleUtil.pause();
                return;
            }

            while (true) {
                ConsoleUtil.printSubHeader("ALLOCATIONS FOR BUDGET: " + budget.getBudgetName());

                List<BudgetCategory> allocations = budgetCategoryDAO.getCategoriesForBudget(budgetId);
                Map<Integer, String> categoryNames = new HashMap<>();
                for (Category c : categoryDAO.getAvailableCategoriesForUser(session.getUserId())) {
                    categoryNames.put(c.getCategoryId(), c.getCategoryName() + " (" + c.getCategoryType() + ")");
                }

                if (allocations.isEmpty()) {
                    System.out.println("No category allocations configured yet for this budget.");
                } else {
                    System.out.println(String.format(" %-5s | %-28s | %s", "Cat ID", "Category", "Allocated Limit"));
                    ConsoleUtil.printDivider();
                    BigDecimal total = BigDecimal.ZERO;
                    for (BudgetCategory bc : allocations) {
                        String catName = categoryNames.getOrDefault(bc.getCategoryId(), "#" + bc.getCategoryId());
                        System.out.println(String.format(" #%-5d | %-28s | %s",
                                bc.getCategoryId(),
                                catName,
                                ConsoleUtil.formatCurrency(bc.getAllocatedAmount())));
                        if (bc.getAllocatedAmount() != null) total = total.add(bc.getAllocatedAmount());
                    }
                    ConsoleUtil.printDivider();
                    System.out.println(String.format(" TOTAL ALLOCATED BUDGET LIMIT : %s", ConsoleUtil.formatCurrency(total)));
                }

                System.out.println("\nAllocation Actions:");
                System.out.println("1. Add Category Allocation");
                System.out.println("2. Update Existing Category Allocation");
                System.out.println("3. Remove Category Allocation");
                System.out.println("4. Return to Budget Management");

                int opt = ConsoleUtil.promptInt("Choice", 1, 4);
                if (opt == 4) break;

                switch (opt) {
                    case 1:
                        addCategoryAllocation(budget, allocations);
                        break;
                    case 2:
                        updateCategoryAllocation(budget, allocations);
                        break;
                    case 3:
                        removeCategoryAllocation(budget, allocations);
                        break;
                }
            }

        } catch (SQLException e) {
            ConsoleUtil.printError("Database error while managing allocations: " + e.getMessage());
            ConsoleUtil.pause();
        }
    }

    private void addCategoryAllocation(Budget budget, List<BudgetCategory> existing) throws SQLException {
        ConsoleUtil.printSubHeader("ADD CATEGORY ALLOCATION TO BUDGET");
        List<Category> available = categoryDAO.getAvailableCategoriesForUser(session.getUserId());

        // Filter out categories already allocated
        Map<Integer, Category> choices = new HashMap<>();
        int count = 0;
        for (Category c : available) {
            boolean alreadyIn = false;
            for (BudgetCategory bc : existing) {
                if (bc.getCategoryId().equals(c.getCategoryId())) {
                    alreadyIn = true;
                    break;
                }
            }
            if (!alreadyIn) {
                count++;
                choices.put(count, c);
                System.out.println(count + ". #" + c.getCategoryId() + " " + c.getCategoryName() + " (" + c.getCategoryType() + ")");
            }
        }

        if (count == 0) {
            ConsoleUtil.printInfo("All available categories are already allocated in this budget.");
            ConsoleUtil.pause();
            return;
        }

        int catIdx = ConsoleUtil.promptInt("Select Category", 1, count);
        Category chosen = choices.get(catIdx);

        BigDecimal amount = ConsoleUtil.promptBigDecimal("Allocated Limit Amount (₹, must be > 0)", false, true);

        BudgetCategory bc = new BudgetCategory(budget.getBudgetId(), chosen.getCategoryId(), amount);
        boolean ok = budgetCategoryDAO.addCategoryToBudget(bc);
        if (ok) {
            ConsoleUtil.printSuccess("Category '" + chosen.getCategoryName() + "' allocated " + ConsoleUtil.formatCurrency(amount) + " successfully!");
        } else {
            ConsoleUtil.printError("Failed to add allocation.");
        }
        ConsoleUtil.pause();
    }

    private void updateCategoryAllocation(Budget budget, List<BudgetCategory> existing) throws SQLException {
        ConsoleUtil.printSubHeader("UPDATE CATEGORY ALLOCATION AMOUNT");
        if (existing.isEmpty()) {
            ConsoleUtil.printInfo("No categories are allocated yet.");
            ConsoleUtil.pause();
            return;
        }

        int catId = ConsoleUtil.promptInt("Enter Category ID to adjust allocation (or 0 to cancel)", 0, Integer.MAX_VALUE);
        if (catId == 0) return;

        BudgetCategory target = null;
        for (BudgetCategory bc : existing) {
            if (bc.getCategoryId().equals(catId)) {
                target = bc;
                break;
            }
        }

        if (target == null) {
            ConsoleUtil.printError("Category #" + catId + " is not mapped in this budget.");
            ConsoleUtil.pause();
            return;
        }

        System.out.println("Current Allocation: " + ConsoleUtil.formatCurrency(target.getAllocatedAmount()));
        BigDecimal newAmount = ConsoleUtil.promptBigDecimal("New Allocated Amount (₹, must be > 0)", false, true);

        boolean ok = budgetCategoryDAO.updateAllocatedAmount(budget.getBudgetId(), catId, newAmount);
        if (ok) {
            ConsoleUtil.printSuccess("Allocation updated to " + ConsoleUtil.formatCurrency(newAmount) + " successfully!");
        } else {
            ConsoleUtil.printError("Failed to update allocation.");
        }
        ConsoleUtil.pause();
    }

    private void removeCategoryAllocation(Budget budget, List<BudgetCategory> existing) throws SQLException {
        ConsoleUtil.printSubHeader("REMOVE CATEGORY ALLOCATION");
        if (existing.isEmpty()) {
            ConsoleUtil.printInfo("No categories are allocated yet.");
            ConsoleUtil.pause();
            return;
        }

        int catId = ConsoleUtil.promptInt("Enter Category ID to remove (or 0 to cancel)", 0, Integer.MAX_VALUE);
        if (catId == 0) return;

        boolean confirm = ConsoleUtil.promptConfirm("Remove category #" + catId + " from budget '" + budget.getBudgetName() + "'?");
        if (!confirm) {
            ConsoleUtil.printInfo("Removal cancelled.");
            ConsoleUtil.pause();
            return;
        }

        boolean ok = budgetCategoryDAO.removeCategoryFromBudget(budget.getBudgetId(), catId);
        if (ok) {
            ConsoleUtil.printSuccess("Category allocation removed.");
        } else {
            ConsoleUtil.printError("Failed to remove category allocation.");
        }
        ConsoleUtil.pause();
    }
}
