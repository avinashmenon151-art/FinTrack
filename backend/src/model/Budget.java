package model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Model representing the 'budgets' entity in FinTrack.
 */
public class Budget {

    private Integer budgetId;
    private Integer userId;
    private String budgetName;
    private LocalDate startDate;
    private LocalDate endDate;
    private OffsetDateTime createdAt;

    public Budget() {
    }

    public Budget(Integer budgetId, Integer userId, String budgetName,
                  LocalDate startDate, LocalDate endDate, OffsetDateTime createdAt) {
        this.budgetId = budgetId;
        this.userId = userId;
        this.budgetName = budgetName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdAt = createdAt;
    }

    public Integer getBudgetId() {
        return budgetId;
    }

    public void setBudgetId(Integer budgetId) {
        this.budgetId = budgetId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getBudgetName() {
        return budgetName;
    }

    public void setBudgetName(String budgetName) {
        this.budgetName = budgetName;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Budget{" +
                "budgetId=" + budgetId +
                ", userId=" + userId +
                ", budgetName='" + budgetName + '\'' +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", createdAt=" + createdAt +
                '}';
    }
}
