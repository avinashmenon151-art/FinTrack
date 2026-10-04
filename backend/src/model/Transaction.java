package model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Model representing the 'transactions' entity in FinTrack.
 * Note: userId is retained for controlled denormalization and query performance.
 */
public class Transaction {

    private Long transactionId;
    private Integer userId;
    private Integer accountId;
    private Integer categoryId;
    private BigDecimal amount;
    private TransactionType transactionType;
    private LocalDate transactionDate;
    private String description;
    private OffsetDateTime createdAt;

    public Transaction() {
    }

    public Transaction(Long transactionId, Integer userId, Integer accountId, Integer categoryId,
                       BigDecimal amount, TransactionType transactionType, LocalDate transactionDate,
                       String description, OffsetDateTime createdAt) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.accountId = accountId;
        this.categoryId = categoryId;
        this.amount = amount;
        this.transactionType = transactionType;
        this.transactionDate = transactionDate;
        this.description = description;
        this.createdAt = createdAt;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
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

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "transactionId=" + transactionId +
                ", userId=" + userId +
                ", accountId=" + accountId +
                ", categoryId=" + categoryId +
                ", amount=" + amount +
                ", transactionType=" + transactionType +
                ", transactionDate=" + transactionDate +
                ", description='" + description + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
