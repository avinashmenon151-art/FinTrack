package model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Model representing the 'savings_goals' entity in FinTrack.
 * Note: accountId is optional (may be null).
 */
public class SavingsGoal {

    private Integer goalId;
    private Integer userId;
    private Integer accountId;
    private String goalName;
    private BigDecimal targetAmount;
    private BigDecimal savedAmount;
    private LocalDate targetDate;
    private SavingsGoalStatus status;
    private OffsetDateTime createdAt;

    public SavingsGoal() {
    }

    public SavingsGoal(Integer goalId, Integer userId, Integer accountId, String goalName,
                       BigDecimal targetAmount, BigDecimal savedAmount, LocalDate targetDate,
                       SavingsGoalStatus status, OffsetDateTime createdAt) {
        this.goalId = goalId;
        this.userId = userId;
        this.accountId = accountId;
        this.goalName = goalName;
        this.targetAmount = targetAmount;
        this.savedAmount = savedAmount;
        this.targetDate = targetDate;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Integer getGoalId() {
        return goalId;
    }

    public void setGoalId(Integer goalId) {
        this.goalId = goalId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public void setAccountId(Integer accountId) {
        this.accountId = accountId;
    }

    public String getGoalName() {
        return goalName;
    }

    public void setGoalName(String goalName) {
        this.goalName = goalName;
    }

    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    public void setTargetAmount(BigDecimal targetAmount) {
        this.targetAmount = targetAmount;
    }

    public BigDecimal getSavedAmount() {
        return savedAmount;
    }

    public void setSavedAmount(BigDecimal savedAmount) {
        this.savedAmount = savedAmount;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    public SavingsGoalStatus getStatus() {
        return status;
    }

    public void setStatus(SavingsGoalStatus status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "SavingsGoal{" +
                "goalId=" + goalId +
                ", userId=" + userId +
                ", accountId=" + accountId +
                ", goalName='" + goalName + '\'' +
                ", targetAmount=" + targetAmount +
                ", savedAmount=" + savedAmount +
                ", targetDate=" + targetDate +
                ", status=" + status +
                ", createdAt=" + createdAt +
                '}';
    }
}
