package model;

import java.math.BigDecimal;

/**
 * Model representing the associative entity 'budget_category' in FinTrack.
 * Represents M:N relationship between budgets and categories.
 */
public class BudgetCategory {

    private Integer budgetId;
    private Integer categoryId;
    private BigDecimal allocatedAmount;

    public BudgetCategory() {
    }

    public BudgetCategory(Integer budgetId, Integer categoryId, BigDecimal allocatedAmount) {
        this.budgetId = budgetId;
        this.categoryId = categoryId;
        this.allocatedAmount = allocatedAmount;
    }

    public Integer getBudgetId() {
        return budgetId;
    }

    public void setBudgetId(Integer budgetId) {
        this.budgetId = budgetId;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public BigDecimal getAllocatedAmount() {
        return allocatedAmount;
    }

    public void setAllocatedAmount(BigDecimal allocatedAmount) {
        this.allocatedAmount = allocatedAmount;
    }

    @Override
    public String toString() {
        return "BudgetCategory{" +
                "budgetId=" + budgetId +
                ", categoryId=" + categoryId +
                ", allocatedAmount=" + allocatedAmount +
                '}';
    }
}
