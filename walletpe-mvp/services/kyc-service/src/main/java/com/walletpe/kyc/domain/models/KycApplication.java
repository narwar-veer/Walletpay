package com.walletpe.kyc.domain.models;

import com.walletpe.kyc.domain.enums.KycStatus;
import com.walletpe.kyc.domain.enums.RejectionReason;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "kyc_applications", uniqueConstraints = {
    @UniqueConstraint(name = "uk_kyc_application_user_id", columnNames = "user_id"),
    @UniqueConstraint(name = "uk_kyc_application_aadhaar_hash", columnNames = "aadhaar_hash")
}, indexes = {
    @Index(name = "idx_kyc_user_id", columnList = "user_id"),
    @Index(name = "idx_kyc_aadhaar_hash", columnList = "aadhaar_hash")
})
public class KycApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "kyc_id", nullable = false, updatable = false)
    private UUID kycId;

    // External reference to User Service (no SQL FK)
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    // Masked value e.g. "XXXXXXXX1234", safe for logs/display
    @Column(name = "aadhaar_reference", nullable = false)
    private String aadhaarReference;

    // Cryptographic hash (salted SHA-256) of raw Aadhaar for duplicate detection
    @Column(name = "aadhaar_hash", nullable = false)
    private String aadhaarHash;

    @Column(name = "document_reference", nullable = false)
    private String documentReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private KycStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason")
    private RejectionReason rejectionReason;

    @Column(name = "rejection_notes")
    private String rejectionNotes;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public KycApplication() {}

    public UUID getKycId() {
        return kycId;
    }

    public void setKycId(UUID kycId) {
        this.kycId = kycId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getAadhaarReference() {
        return aadhaarReference;
    }

    public void setAadhaarReference(String aadhaarReference) {
        this.aadhaarReference = aadhaarReference;
    }

    public String getAadhaarHash() {
        return aadhaarHash;
    }

    public void setAadhaarHash(String aadhaarHash) {
        this.aadhaarHash = aadhaarHash;
    }

    public String getDocumentReference() {
        return documentReference;
    }

    public void setDocumentReference(String documentReference) {
        this.documentReference = documentReference;
    }

    public KycStatus getStatus() {
        return status;
    }

    public void setStatus(KycStatus status) {
        this.status = status;
    }

    public RejectionReason getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(RejectionReason rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getRejectionNotes() {
        return rejectionNotes;
    }

    public void setRejectionNotes(String rejectionNotes) {
        this.rejectionNotes = rejectionNotes;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(UUID reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
