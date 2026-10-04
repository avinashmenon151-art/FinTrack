package model;

import java.time.OffsetDateTime;

/**
 * Model representing the 'users' entity in FinTrack.
 */
public class User {

    private Integer userId;
    private String name;
    private String email;
    private String passwordHash;
    private String phoneNumber;
    private Integer defaultAccountId;
    private OffsetDateTime createdAt;

    public User() {
    }

    public User(Integer userId, String name, String email, String passwordHash,
                String phoneNumber, Integer defaultAccountId, OffsetDateTime createdAt) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.phoneNumber = phoneNumber;
        this.defaultAccountId = defaultAccountId;
        this.createdAt = createdAt;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Integer getDefaultAccountId() {
        return defaultAccountId;
    }

    public void setDefaultAccountId(Integer defaultAccountId) {
        this.defaultAccountId = defaultAccountId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "User{" +
                "userId=" + userId +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", defaultAccountId=" + defaultAccountId +
                ", createdAt=" + createdAt +
                '}';
    }
}
