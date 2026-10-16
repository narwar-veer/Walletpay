package com.walletpe.kyc.domain.models;

import jakarta.persistence.*;
import com.walletpe.kyc.domain.enums.KycStatus;
import com.walletpe.kyc.domain.enums.RejectionReason;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "kyc_applications")
@Getter
@Setter
@NoArgsConstructor
public class KycApplication {

     @Id
     @GeneratedValue(strategy = GenerationType.UUID)
     @Column(name = "kyc_id", nullable = false, updatable = false)
     private UUID kycId;

     // User Service owns user records. This is an external reference, not a foreign key.
     @Column(name = "user_id", nullable = false)
     private UUID userId;

     @Column(name = "full_name", nullable = false)
     private String fullName;

     @Column(name = "date_of_birth", nullable = false)
     private LocalDate dateOfBirth;

     @Column(name = "aadhaar_reference", nullable = false)
     private String aadhaarReference;

     @Column(name = "document_reference", nullable = false)
     private String documentReference;

     @Enumerated(EnumType.STRING)
     @Column(name = "status", nullable = false)
     private KycStatus status;

     @Enumerated(EnumType.STRING)
     @Column(name = "rejection_reason")
     private RejectionReason rejectionReason;

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

}
