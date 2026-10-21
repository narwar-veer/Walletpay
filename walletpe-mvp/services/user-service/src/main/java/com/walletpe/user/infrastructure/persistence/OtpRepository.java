package com.walletpe.user.infrastructure.persistence;

import com.walletpe.user.domain.enums.OtpPurpose;
import com.walletpe.user.domain.models.OtpChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpRepository extends JpaRepository<OtpChallenge, UUID> {
    Optional<OtpChallenge> findFirstByTargetIdentifierAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
            String targetIdentifier, OtpPurpose purpose);

    List<OtpChallenge> findByTargetIdentifierAndPurposeAndConsumedFalse(
            String targetIdentifier, OtpPurpose purpose);
}
