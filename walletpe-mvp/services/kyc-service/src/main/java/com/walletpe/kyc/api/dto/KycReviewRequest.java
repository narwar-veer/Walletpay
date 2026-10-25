package com.walletpe.kyc.api.dto;

import com.walletpe.kyc.domain.enums.RejectionReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record KycReviewRequest(
        @NotBlank(message = "Review action is required")
        @Pattern(regexp = "^(?i)(APPROVE|REJECT)$", message = "Action must be either APPROVE or REJECT")
        String action,

        RejectionReason rejectionReason,

        String rejectionNotes
) {}
