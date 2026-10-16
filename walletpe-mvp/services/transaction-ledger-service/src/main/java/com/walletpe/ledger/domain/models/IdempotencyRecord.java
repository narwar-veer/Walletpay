package com.walletpe.ledger.domain.models;

import jakarta.persistence.*;
import com.walletpe.ledger.domain.enums.IdempotencyStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "idempotency_records",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_idempotency_requester_key",
            columnNames = {"requester_user_id", "idempotency_key"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "idempotency_record_id", nullable = false, updatable = false)
    private UUID idempotencyRecordId;

    @Column(name = "requester_user_id", nullable = false)
    private UUID requesterUserId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IdempotencyStatus status;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

}
