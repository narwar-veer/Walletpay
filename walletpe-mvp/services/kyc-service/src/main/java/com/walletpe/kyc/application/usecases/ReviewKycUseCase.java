package com.walletpe.kyc.application.usecases;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walletpe.kyc.api.dto.KycReviewRequest;
import com.walletpe.kyc.api.dto.KycStatusResponse;
import com.walletpe.kyc.domain.enums.KycStatus;
import com.walletpe.kyc.domain.models.KycApplication;
import com.walletpe.kyc.domain.models.KycOutboxEvent;
import com.walletpe.kyc.infrastructure.persistence.KycApplicationRepository;
import com.walletpe.kyc.infrastructure.persistence.KycOutboxRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ReviewKycUseCase {

    private final KycApplicationRepository kycApplicationRepository;
    private final KycOutboxRepository kycOutboxRepository;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public ReviewKycUseCase(KycApplicationRepository kycApplicationRepository,
                            KycOutboxRepository kycOutboxRepository,
                            Clock clock,
                            ObjectMapper objectMapper) {
        this.kycApplicationRepository = kycApplicationRepository;
        this.kycOutboxRepository = kycOutboxRepository;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public KycStatusResponse review(UUID adminId, UUID kycId, KycReviewRequest request, String correlationId) {
        if (adminId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin identity is missing");
        }

        KycApplication application = kycApplicationRepository.findById(kycId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "KYC application not found"));

        if (application.getStatus() != KycStatus.SUBMITTED && application.getStatus() != KycStatus.UNDER_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a submitted KYC application can be reviewed");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String action = request.action().toUpperCase(Locale.ROOT);

        if ("APPROVE".equals(action)) {
            application.setStatus(KycStatus.APPROVED);
            application.setRejectionReason(null);
            application.setRejectionNotes(null);
            application.setReviewedBy(adminId);
            application.setReviewedAt(now);

            // Persist Transactional Outbox Event in the same atomic database transaction
            UUID eventId = UUID.randomUUID();
            Map<String, Object> payloadMap = new LinkedHashMap<>();
            payloadMap.put("eventId", eventId.toString());
            payloadMap.put("eventType", "KYC_APPROVED");
            payloadMap.put("kycId", application.getKycId().toString());
            payloadMap.put("userId", application.getUserId().toString());
            payloadMap.put("reviewerId", adminId.toString());
            payloadMap.put("occurredAt", now.toString());
            payloadMap.put("correlationId", correlationId != null ? correlationId : UUID.randomUUID().toString());

            String payloadJson;
            try {
                payloadJson = objectMapper.writeValueAsString(payloadMap);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize outbox event payload", e);
            }

            KycOutboxEvent outboxEvent = new KycOutboxEvent(
                    eventId,
                    "KYC",
                    application.getKycId().toString(),
                    application.getUserId().toString(),
                    "KYC_APPROVED",
                    payloadJson,
                    "PENDING"
            );
            kycOutboxRepository.save(outboxEvent);

        } else if ("REJECT".equals(action)) {
            if (request.rejectionReason() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rejection reason is required when rejecting KYC");
            }
            application.setStatus(KycStatus.REJECTED);
            application.setRejectionReason(request.rejectionReason());
            application.setRejectionNotes(request.rejectionNotes());
            application.setReviewedBy(adminId);
            application.setReviewedAt(now);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown review action: " + request.action());
        }

        KycApplication saved = kycApplicationRepository.save(application);
        return SubmitKycUseCase.toResponse(saved);
    }
}
