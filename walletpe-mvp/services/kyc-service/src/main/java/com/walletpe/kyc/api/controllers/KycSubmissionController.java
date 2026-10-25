package com.walletpe.kyc.api.controllers;

import com.walletpe.kyc.api.dto.KycStatusResponse;
import com.walletpe.kyc.api.dto.KycSubmitRequest;
import com.walletpe.kyc.application.usecases.SubmitKycUseCase;
import com.walletpe.kyc.api.security.GatewayRequestVerifier;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kyc")
public class KycSubmissionController {

    private final SubmitKycUseCase submitKycUseCase;
    private final GatewayRequestVerifier gatewayRequestVerifier;

    public KycSubmissionController(SubmitKycUseCase submitKycUseCase, GatewayRequestVerifier gatewayRequestVerifier) {
        this.submitKycUseCase = submitKycUseCase;
        this.gatewayRequestVerifier = gatewayRequestVerifier;
    }

    /**
     * Submit KYC via JSON payload (with document base64).
     * Gateway enforces authentication and injects X-User-Id.
     */
    @PostMapping(value = "/submit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<KycStatusResponse> submitKycJson(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-Internal-Secret", required = false) String gatewaySecret,
            @Valid @RequestBody KycSubmitRequest request) {
        gatewayRequestVerifier.verify(gatewaySecret);
        UUID userId = parseUserId(userIdHeader);
        KycStatusResponse response = submitKycUseCase.submitWithBase64(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Submit KYC via multipart/form-data payload (with file upload).
     */
    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KycStatusResponse> submitKycMultipart(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-Internal-Secret", required = false) String gatewaySecret,
            @Valid @RequestPart("data") KycSubmitRequest request,
            @RequestPart("file") MultipartFile file) {
        gatewayRequestVerifier.verify(gatewaySecret);
        UUID userId = parseUserId(userIdHeader);
        KycStatusResponse response = submitKycUseCase.submitWithFile(userId, request, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private UUID parseUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Caller identity header (X-User-Id) is missing");
        }
        try {
            return UUID.fromString(userIdHeader.trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid caller identity header format");
        }
    }
}
