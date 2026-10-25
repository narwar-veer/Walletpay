package com.walletpe.kyc.infrastructure.persistence;

import com.walletpe.kyc.domain.models.KycApplication;
import com.walletpe.kyc.domain.models.KycDocumentReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KycDocumentReferenceRepository extends JpaRepository<KycDocumentReference, UUID> {
    List<KycDocumentReference> findByKycApplication(KycApplication kycApplication);
    void deleteByKycApplication(KycApplication kycApplication);
}
