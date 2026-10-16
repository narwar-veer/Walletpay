package com.walletpe.audit.domain.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.walletpe.audit.domain.enums.ActorType;
import com.walletpe.audit.domain.enums.AuditResult;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "audit_id", nullable = false, updatable = false)
    private UUID auditId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false)
    private ActorType actorType;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    private AuditResult result;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

}
