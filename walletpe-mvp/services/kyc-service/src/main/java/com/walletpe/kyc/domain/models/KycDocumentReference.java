package com.walletpe.kyc.domain.models;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "kyc_document_references")
public class KycDocumentReference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kyc_id", nullable = false)
    private KycApplication kycApplication;

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public KycDocumentReference() {}

    public KycDocumentReference(KycApplication kycApplication, String documentType,
                                String storagePath, String contentType, Long fileSize) {
        this.kycApplication = kycApplication;
        this.documentType = documentType;
        this.storagePath = storagePath;
        this.contentType = contentType;
        this.fileSize = fileSize;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public KycApplication getKycApplication() {
        return kycApplication;
    }

    public void setKycApplication(KycApplication kycApplication) {
        this.kycApplication = kycApplication;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
