package com.walletpe.ledger.domain.models;

import jakarta.persistence.*;
import com.walletpe.ledger.domain.enums.TransactionStatus;
import com.walletpe.ledger.domain.enums.TransactionType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    // Uniqueness is enforced by IdempotencyRecord using requester_user_id + key.
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "requester_user_id", nullable = false)
    private UUID requesterUserId;

    // Wallet Service owns wallets. These are external references, not foreign keys.
    @Column(name = "sender_wallet_id")
    private UUID senderWalletId;

    @Column(name = "recipient_wallet_id", nullable = false)
    private UUID recipientWalletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;

    @Column(name = "reversal_of_transaction_id")
    private UUID reversalOfTransactionId;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

}
