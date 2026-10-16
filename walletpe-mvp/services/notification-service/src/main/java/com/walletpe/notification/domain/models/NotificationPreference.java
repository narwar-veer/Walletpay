package com.walletpe.notification.domain.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.walletpe.notification.domain.enums.NotificationChannel;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "notification_preferences",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_user_channel_preference",
            columnNames = {"user_id", "channel"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "preference_id", nullable = false, updatable = false)
    private UUID preferenceId;

    // User Service owns user records. This is an external reference, not a foreign key.
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private NotificationChannel channel;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

}
