package com.walletpe.kyc.infrastructure.persistence;

import com.walletpe.kyc.domain.models.KycApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface KycApplicationRepository extends JpaRepository<KycApplication, UUID> {
    Optional<KycApplication> findByUserId(UUID userId);
    Optional<KycApplication> findByAadhaarHash(String aadhaarHash);
    boolean existsByAadhaarHashAndUserIdNot(String aadhaarHash, UUID userId);
    boolean existsByAadhaarHash(String aadhaarHash);
}
