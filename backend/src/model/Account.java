package model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Model representing the 'accounts' entity in FinTrack.
 */
public class Account {

    private Integer accountId;
    private Integer userId;
    private String accountName;
    private String accountType;
    private BigDecimal balance;
    private OffsetDateTime createdAt;

    public Account() {
    }

    public Account(Integer accountId, Integer userId, String accountName,
                   String accountType, BigDecimal balance, OffsetDateTime createdAt) {
        this.accountId = accountId;
        this.userId = userId;
        this.accountName = accountName;
        this.accountType = accountType;
        this.balance = balance;
        this.createdAt = createdAt;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public void setAccountId(Integer accountId) {
        this.accountId = accountId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Account{" +
                "accountId=" + accountId +
                ", userId=" + userId +
                ", accountName='" + accountName + '\'' +
                ", accountType='" + accountType + '\'' +
                ", balance=" + balance +
                ", createdAt=" + createdAt +
                '}';
    }
}
