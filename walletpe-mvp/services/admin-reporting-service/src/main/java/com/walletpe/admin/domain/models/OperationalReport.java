package com.walletpe.admin.domain.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "operational_reports")
@Getter
@Setter
@NoArgsConstructor
public class OperationalReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "report_id", nullable = false, updatable = false)
    private UUID reportId;

    @Column(name = "report_name", nullable = false)
    private String reportName;

    @Column(name = "report_type", nullable = false)
    private String reportType;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "summary_data", columnDefinition = "jsonb")
    private String summaryData;

    @Column(name = "generated_by")
    private UUID generatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

}
