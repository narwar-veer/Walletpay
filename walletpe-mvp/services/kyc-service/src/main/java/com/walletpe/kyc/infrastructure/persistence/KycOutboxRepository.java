package com.walletpe.kyc.infrastructure.persistence;

import com.walletpe.kyc.domain.models.KycOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KycOutboxRepository extends JpaRepository<KycOutboxEvent, UUID> {
    List<KycOutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(String status);
}
