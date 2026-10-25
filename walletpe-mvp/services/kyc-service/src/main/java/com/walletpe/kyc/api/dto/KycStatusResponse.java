package com.walletpe.kyc.api.dto;

import com.walletpe.kyc.domain.enums.KycStatus;
import com.walletpe.kyc.domain.enums.RejectionReason;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record KycStatusResponse(
        UUID kycId,
        KycStatus status,
        String fullName,
        LocalDate dateOfBirth,
        String aadhaarReference,
        RejectionReason rejectionReason,
        String rejectionNotes,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt
) {}
