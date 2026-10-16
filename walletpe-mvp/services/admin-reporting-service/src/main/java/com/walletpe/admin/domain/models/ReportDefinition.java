package com.walletpe.admin.domain.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.walletpe.admin.domain.enums.ReportStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "report_definitions")
@Getter
@Setter
@NoArgsConstructor
public class ReportDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "report_id", nullable = false, updatable = false)
    private UUID reportId;

    @Column(name = "report_type", nullable = false)
    private String reportType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReportStatus status;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "generated_by")
    private UUID generatedBy;

    @Column(name = "storage_reference")
    private String storageReference;

}
