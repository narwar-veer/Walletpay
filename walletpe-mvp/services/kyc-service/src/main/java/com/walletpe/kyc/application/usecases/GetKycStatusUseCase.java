package com.walletpe.kyc.application.usecases;

import com.walletpe.kyc.api.dto.KycStatusResponse;
import com.walletpe.kyc.domain.enums.KycStatus;
import com.walletpe.kyc.infrastructure.persistence.KycApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class GetKycStatusUseCase {

    private final KycApplicationRepository kycApplicationRepository;

    public GetKycStatusUseCase(KycApplicationRepository kycApplicationRepository) {
        this.kycApplicationRepository = kycApplicationRepository;
    }

    @Transactional(readOnly = true)
    public KycStatusResponse getStatus(UUID userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Caller identity is missing");
        }

        return kycApplicationRepository.findByUserId(userId)
                .map(SubmitKycUseCase::toResponse)
                .orElse(new KycStatusResponse(
                        null,
                        KycStatus.NOT_STARTED,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
    }
}
