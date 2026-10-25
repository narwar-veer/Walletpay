package com.walletpe.kyc.application.usecases;

import com.walletpe.kyc.api.dto.KycHoldRequest;
import com.walletpe.kyc.api.dto.KycStatusResponse;
import com.walletpe.kyc.domain.enums.KycStatus;
import com.walletpe.kyc.domain.models.KycApplication;
import com.walletpe.kyc.infrastructure.persistence.KycApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PutKycOnHoldUseCase {

    private final KycApplicationRepository kycApplicationRepository;
    private final Clock clock;

    public PutKycOnHoldUseCase(KycApplicationRepository kycApplicationRepository, Clock clock) {
        this.kycApplicationRepository = kycApplicationRepository;
        this.clock = clock;
    }

    @Transactional
    public KycStatusResponse hold(UUID adminId, UUID kycId, KycHoldRequest request) {
        if (adminId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin identity is missing");
        }

        KycApplication application = kycApplicationRepository.findById(kycId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "KYC application not found"));

        if (application.getStatus() != KycStatus.SUBMITTED && application.getStatus() != KycStatus.UNDER_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an in-progress KYC application can be put on hold");
        }

        application.setStatus(KycStatus.ON_HOLD);
        application.setRejectionNotes(request != null ? request.reason() : "Application placed on hold");
        application.setReviewedBy(adminId);
        application.setReviewedAt(LocalDateTime.now(clock));

        KycApplication saved = kycApplicationRepository.save(application);
        return SubmitKycUseCase.toResponse(saved);
    }
}
