package com.walletpe.admin.domain.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "report_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class ReportSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "snapshot_id", nullable = false, updatable = false)
    private UUID snapshotId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private ReportDefinition reportDefinition;

    @Column(name = "snapshot_data", nullable = false, columnDefinition = "jsonb")
    private String snapshotData;

    @CreationTimestamp
    @Column(name = "captured_at", nullable = false, updatable = false)
    private LocalDateTime capturedAt;

}
