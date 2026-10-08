package cli;

import dao.CategoryDAO;
import model.Category;
import model.CategoryType;

import java.sql.SQLException;
import java.util.List;

/**
 * Category management view for FinTrack CLI.
 * Displays shared system categories alongside user-owned custom categories.
 * Allows adding, editing, and deleting custom categories while guaranteeing
 * that system categories remain strictly read-only.
 */
public class CategoriesView {

    private final SessionState session;
    private final CategoryDAO categoryDAO;

    public CategoriesView(SessionState session, CategoryDAO categoryDAO) {
        this.session = session;
        this.categoryDAO = categoryDAO;
    }

    public void run() {
        while (true) {
            ConsoleUtil.printSubHeader("CATEGORY MANAGEMENT");
            System.out.println("1. List All Categories (System & Custom)");
            System.out.println("2. Add Custom Category");
            System.out.println("3. Edit Custom Category");
            System.out.println("4. Delete Custom Category");
            System.out.println("5. Return to Main Menu");

            int choice = ConsoleUtil.promptInt("Enter choice", 1, 5);
            switch (choice) {
                case 1:
                    listCategories();
                    ConsoleUtil.pause();
                    break;
                case 2:
                    addCustomCategory();
                    break;
                case 3:
                    editCustomCategory();
                    break;
                case 4:
                    deleteCustomCategory();
                    break;
                case 5:
                    return;
            }
        }
    }

    private void listCategories() {
        try {
            List<Category> systemCats = categoryDAO.getSystemCategories();
            List<Category> customCats = categoryDAO.getCategoriesByUserId(session.getUserId());

            ConsoleUtil.printSubHeader("SYSTEM CATEGORIES (Shared, Read-Only: " + systemCats.size() + ")");
            System.out.println(String.format(" %-5s | %-20s | %-8s | %s", "ID", "Name", "Type", "Description"));
            ConsoleUtil.printDivider();
            for (Category c : systemCats) {
                System.out.println(String.format(" #%-4d | %-20s | %-8s | %s",
                        c.getCategoryId(),
                        c.getCategoryName(),
                        c.getCategoryType(),
                        c.getDescription() != null ? c.getDescription() : ""));
            }

            ConsoleUtil.printSubHeader("YOUR CUSTOM CATEGORIES (" + customCats.size() + ")");
            if (customCats.isEmpty()) {
                ConsoleUtil.printInfo("No custom categories created yet. Use option 2 to create one.");
            } else {
                System.out.println(String.format(" %-5s | %-20s | %-8s | %s", "ID", "Name", "Type", "Description"));
                ConsoleUtil.printDivider();
                for (Category c : customCats) {
                    System.out.println(String.format(" #%-4d | %-20s | %-8s | %s",
                            c.getCategoryId(),
                            c.getCategoryName(),
                            c.getCategoryType(),
                            c.getDescription() != null ? c.getDescription() : ""));
                }
            }
        } catch (SQLException e) {
            ConsoleUtil.printError("Failed to fetch categories: " + e.getMessage());
        }
    }

    private void addCustomCategory() {
        ConsoleUtil.printSubHeader("ADD CUSTOM CATEGORY");
        String name = ConsoleUtil.promptString("Category Name");

        System.out.println("\nCategory Classification:");
        System.out.println("1. Expense");
        System.out.println("2. Income");
        int typeChoice = ConsoleUtil.promptInt("Choice", 1, 2);
        CategoryType type = (typeChoice == 1) ? CategoryType.Expense : CategoryType.Income;

        String description = ConsoleUtil.promptOptionalString("Description", "");

        Category cat = new Category();
        cat.setUserId(session.getUserId());
        cat.setCategoryName(name);
        cat.setCategoryType(type);
        cat.setDescription(description);

        try {
            Category created = categoryDAO.createCategory(cat);
            ConsoleUtil.printSuccess("Custom category '" + created.getCategoryName() + "' created with ID #" + created.getCategoryId() + "!");
        } catch (SQLException e) {
            ConsoleUtil.printError("Failed to create category: " + e.getMessage());
        }
        ConsoleUtil.pause();
    }

    private void editCustomCategory() {
        ConsoleUtil.printSubHeader("EDIT CUSTOM CATEGORY");
        int catId = ConsoleUtil.promptInt("Enter Category ID to edit (or 0 to cancel)", 0, Integer.MAX_VALUE);
        if (catId == 0) return;

        try {
            Category existing = categoryDAO.findById(catId);
            if (existing == null) {
                ConsoleUtil.printError("Category #" + catId + " not found.");
                ConsoleUtil.pause();
                return;
            }

            // Enforce system category immutability
            if (existing.getUserId() == null) {
                ConsoleUtil.printError("Category #" + catId + " is a standard System category and cannot be edited.");
                ConsoleUtil.pause();
                return;
            }

            // Enforce user ownership
            if (!existing.getUserId().equals(session.getUserId())) {
                ConsoleUtil.printError("Category #" + catId + " belongs to another user. Access denied.");
                ConsoleUtil.pause();
                return;
            }

            System.out.println("\nEditing: " + existing.getCategoryName() + " (" + existing.getCategoryType() + ")");
            String newName = ConsoleUtil.promptOptionalString("New Category Name", existing.getCategoryName());

            System.out.println("Type: 1. Keep " + existing.getCategoryType() + " | 2. Expense | 3. Income");
            int tChoice = ConsoleUtil.promptInt("Choice", 1, 3);
            if (tChoice == 2) existing.setCategoryType(CategoryType.Expense);
            if (tChoice == 3) existing.setCategoryType(CategoryType.Income);

            String newDesc = ConsoleUtil.promptOptionalString("New Description", existing.getDescription());

            existing.setCategoryName(newName);
            existing.setDescription(newDesc);

            boolean ok = categoryDAO.updateCategory(existing);
            if (ok) {
                ConsoleUtil.printSuccess("Category #" + catId + " updated successfully!");
            } else {
                ConsoleUtil.printError("Failed to update category.");
            }

        } catch (SQLException e) {
            ConsoleUtil.printError("Database error during update: " + e.getMessage());
        }
        ConsoleUtil.pause();
    }

    private void deleteCustomCategory() {
        ConsoleUtil.printSubHeader("DELETE CUSTOM CATEGORY");
        int catId = ConsoleUtil.promptInt("Enter Category ID to delete (or 0 to cancel)", 0, Integer.MAX_VALUE);
        if (catId == 0) return;

        try {
            Category existing = categoryDAO.findById(catId);
            if (existing == null) {
                ConsoleUtil.printError("Category #" + catId + " not found.");
                ConsoleUtil.pause();
                return;
            }

            // Enforce system category immutability
            if (existing.getUserId() == null) {
                ConsoleUtil.printError("Category #" + catId + " is a standard System category and cannot be deleted.");
                ConsoleUtil.pause();
                return;
            }

            // Enforce user ownership
            if (!existing.getUserId().equals(session.getUserId())) {
                ConsoleUtil.printError("Category #" + catId + " belongs to another user. Access denied.");
                ConsoleUtil.pause();
                return;
            }

            boolean confirm = ConsoleUtil.promptConfirm("Are you sure you want to delete custom category '" + existing.getCategoryName() + "'?");
            if (!confirm) {
                ConsoleUtil.printInfo("Deletion cancelled.");
                ConsoleUtil.pause();
                return;
            }

            boolean ok = categoryDAO.deleteCategory(catId);
            if (ok) {
                ConsoleUtil.printSuccess("Custom category #" + catId + " was deleted.");
            } else {
                ConsoleUtil.printError("Failed to delete category.");
            }

        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("foreign key")) {
                ConsoleUtil.printError("Cannot delete category because it is currently assigned to existing transactions or budgets.");
            } else {
                ConsoleUtil.printError("Could not delete category: " + e.getMessage());
            }
        }
        ConsoleUtil.pause();
    }
}
