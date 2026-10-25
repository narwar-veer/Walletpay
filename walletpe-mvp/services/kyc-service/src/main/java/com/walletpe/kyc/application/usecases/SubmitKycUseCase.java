package com.walletpe.kyc.application.usecases;

import com.walletpe.kyc.api.dto.KycStatusResponse;
import com.walletpe.kyc.api.dto.KycSubmitRequest;
import com.walletpe.kyc.domain.enums.KycStatus;
import com.walletpe.kyc.domain.models.KycApplication;
import com.walletpe.kyc.domain.models.KycDocumentReference;
import com.walletpe.kyc.domain.utils.AadhaarCryptoUtils;
import com.walletpe.kyc.infrastructure.client.UserServiceClient;
import com.walletpe.kyc.infrastructure.persistence.KycApplicationRepository;
import com.walletpe.kyc.infrastructure.persistence.KycDocumentReferenceRepository;
import com.walletpe.kyc.infrastructure.storage.ProtectedDocumentStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class SubmitKycUseCase {

    private final KycApplicationRepository kycApplicationRepository;
    private final KycDocumentReferenceRepository documentReferenceRepository;
    private final ProtectedDocumentStorage documentStorage;
    private final UserServiceClient userServiceClient;
    private final Clock clock;
    private final String aadhaarHmacKey;

    public SubmitKycUseCase(KycApplicationRepository kycApplicationRepository,
                            KycDocumentReferenceRepository documentReferenceRepository,
                            ProtectedDocumentStorage documentStorage,
                            UserServiceClient userServiceClient,
                            Clock clock,
                            @Value("${kyc.aadhaar-hmac-key}") String aadhaarHmacKey) {
        this.kycApplicationRepository = kycApplicationRepository;
        this.documentReferenceRepository = documentReferenceRepository;
        this.documentStorage = documentStorage;
        this.userServiceClient = userServiceClient;
        this.clock = clock;
        this.aadhaarHmacKey = aadhaarHmacKey;
    }

    @Transactional
    public KycStatusResponse submitWithFile(UUID userId, KycSubmitRequest request, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document file must be provided");
        }
        try {
            return processSubmission(userId, request, file.getBytes(), file.getContentType());
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read document file");
        }
    }

    @Transactional
    public KycStatusResponse submitWithBase64(UUID userId, KycSubmitRequest request) {
        if (request.documentContentBase64() == null || request.documentContentBase64().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document content must be provided");
        }
        byte[] docBytes;
        try {
            docBytes = Base64.getDecoder().decode(request.documentContentBase64().trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Base64 encoding for document");
        }

        return processSubmission(userId, request, docBytes, request.documentContentType());
    }

    private KycStatusResponse processSubmission(UUID userId, KycSubmitRequest request, byte[] documentBytes, String contentType) {
        // 1. Ingress Identity check
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Caller identity is missing");
        }

        // 2. User Service eligibility check
        UserServiceClient.UserEligibilityResponse eligibility = userServiceClient.checkEligibility(userId);
        if (eligibility == null || !eligibility.eligibleForKyc()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "User is not eligible for KYC: email and mobile must be verified, and account must be active");
        }

        // 3. Age verification (minimum 18 years)
        LocalDate today = LocalDate.now(clock);
        if (request.dateOfBirth() == null || Period.between(request.dateOfBirth(), today).getYears() < 18) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User must be at least 18 years old to submit KYC");
        }

        // 4. Aadhaar duplicate detection across accounts
        String aadhaarHash = AadhaarCryptoUtils.hashAadhaar(request.aadhaarNumber(), aadhaarHmacKey);
        if (kycApplicationRepository.existsByAadhaarHashAndUserIdNot(aadhaarHash, userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aadhaar number is already registered to another account");
        }

        // 5. Existing application rules
        Optional<KycApplication> existingOpt = kycApplicationRepository.findByUserId(userId);
        KycApplication application;

        if (existingOpt.isPresent()) {
            application = existingOpt.get();
            if (application.getStatus() == KycStatus.APPROVED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "KYC application is already approved");
            }
            if (application.getStatus() == KycStatus.SUBMITTED || application.getStatus() == KycStatus.UNDER_REVIEW) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "KYC application is already under processing");
            }
            if (application.getStatus() == KycStatus.ON_HOLD) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "KYC application is on hold");
            }
            // If REJECTED -> re-submission allowed
        } else {
            application = new KycApplication();
            application.setUserId(userId);
        }

        // Store only after every business validation has passed, preventing orphan PII files.
        documentStorage.validate(documentBytes, contentType);
        ProtectedDocumentStorage.StoredDocument storedDoc = documentStorage.store(documentBytes, contentType);
        java.util.List<KycDocumentReference> previousDocuments = existingOpt
                .map(documentReferenceRepository::findByKycApplication)
                .orElseGet(java.util.List::of);

        application.setFullName(request.fullName());
        application.setDateOfBirth(request.dateOfBirth());
        application.setAadhaarHash(aadhaarHash);
        application.setAadhaarReference(AadhaarCryptoUtils.maskAadhaar(request.aadhaarNumber()));
        application.setDocumentReference(storedDoc.storagePath());
        application.setStatus(KycStatus.SUBMITTED);
        application.setRejectionReason(null);
        application.setRejectionNotes(null);
        application.setReviewedBy(null);
        application.setReviewedAt(null);
        application.setSubmittedAt(LocalDateTime.now(clock));

        try {
            KycApplication saved = kycApplicationRepository.saveAndFlush(application);
            documentReferenceRepository.saveAndFlush(new KycDocumentReference(
                    saved, request.documentType(), storedDoc.storagePath(), storedDoc.contentType(), storedDoc.fileSize()));

            if (!previousDocuments.isEmpty()) {
                documentReferenceRepository.deleteByKycApplication(saved);
                // Re-save the current reference after removing the old set.
                documentReferenceRepository.save(new KycDocumentReference(
                        saved, request.documentType(), storedDoc.storagePath(), storedDoc.contentType(), storedDoc.fileSize()));
                previousDocuments.forEach(document -> documentStorage.delete(document.getStoragePath()));
            }
            return toResponse(saved);
        } catch (RuntimeException ex) {
            documentStorage.delete(storedDoc.storagePath());
            throw ex;
        }
    }

    public static KycStatusResponse toResponse(KycApplication app) {
        return new KycStatusResponse(
                app.getKycId(),
                app.getStatus(),
                app.getFullName(),
                app.getDateOfBirth(),
                app.getAadhaarReference(),
                app.getRejectionReason(),
                app.getRejectionNotes(),
                app.getSubmittedAt(),
                app.getReviewedAt()
        );
    }
}
