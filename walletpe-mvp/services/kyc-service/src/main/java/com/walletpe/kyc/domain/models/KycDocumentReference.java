package com.walletpe.kyc.domain.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "kyc_document_references")
@Getter
@Setter
@NoArgsConstructor
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

}
