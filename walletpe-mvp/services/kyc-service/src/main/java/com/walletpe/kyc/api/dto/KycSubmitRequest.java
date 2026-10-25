package com.walletpe.kyc.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record KycSubmitRequest(
        @NotBlank(message = "Full name is required")
        String fullName,

        @NotNull(message = "Date of birth is required")
        LocalDate dateOfBirth,

        @NotBlank(message = "Aadhaar number is required")
        @Pattern(regexp = "^\\d{12}$", message = "Aadhaar number must be exactly 12 digits")
        String aadhaarNumber,

        @NotBlank(message = "Document type is required")
        String documentType,

        String documentContentType,

        String documentContentBase64
) {}
