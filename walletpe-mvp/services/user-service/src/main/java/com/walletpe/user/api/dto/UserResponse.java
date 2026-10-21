package com.walletpe.user.api.dto;

import com.walletpe.user.domain.enums.UserRole;
import com.walletpe.user.domain.enums.UserStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class UserResponse {
    private UUID userId;
    private String name;
    private LocalDate dateOfBirth;
    private String email;
    private String mobileNumber;
    private boolean emailVerified;
    private boolean mobileVerified;
    private UserRole role;
    private UserStatus status;
    private String pendingEmail;
    private String pendingMobileNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public UserResponse() {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final UserResponse response = new UserResponse();

        public Builder userId(UUID userId) { response.setUserId(userId); return this; }
        public Builder name(String name) { response.setName(name); return this; }
        public Builder dateOfBirth(LocalDate dateOfBirth) { response.setDateOfBirth(dateOfBirth); return this; }
        public Builder email(String email) { response.setEmail(email); return this; }
        public Builder mobileNumber(String mobileNumber) { response.setMobileNumber(mobileNumber); return this; }
        public Builder emailVerified(boolean emailVerified) { response.setEmailVerified(emailVerified); return this; }
        public Builder mobileVerified(boolean mobileVerified) { response.setMobileVerified(mobileVerified); return this; }
        public Builder role(UserRole role) { response.setRole(role); return this; }
        public Builder status(UserStatus status) { response.setStatus(status); return this; }
        public Builder pendingEmail(String pendingEmail) { response.setPendingEmail(pendingEmail); return this; }
        public Builder pendingMobileNumber(String pendingMobileNumber) { response.setPendingMobileNumber(pendingMobileNumber); return this; }
        public Builder createdAt(LocalDateTime createdAt) { response.setCreatedAt(createdAt); return this; }
        public Builder updatedAt(LocalDateTime updatedAt) { response.setUpdatedAt(updatedAt); return this; }

        public UserResponse build() { return response; }
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public boolean isMobileVerified() { return mobileVerified; }
    public void setMobileVerified(boolean mobileVerified) { this.mobileVerified = mobileVerified; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public String getPendingEmail() { return pendingEmail; }
    public void setPendingEmail(String pendingEmail) { this.pendingEmail = pendingEmail; }

    public String getPendingMobileNumber() { return pendingMobileNumber; }
    public void setPendingMobileNumber(String pendingMobileNumber) { this.pendingMobileNumber = pendingMobileNumber; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
