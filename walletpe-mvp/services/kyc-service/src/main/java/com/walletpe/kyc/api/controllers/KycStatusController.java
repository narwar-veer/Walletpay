package com.walletpe.kyc.api.controllers;

import com.walletpe.kyc.api.dto.KycStatusResponse;
import com.walletpe.kyc.application.usecases.GetKycStatusUseCase;
import com.walletpe.kyc.api.security.GatewayRequestVerifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kyc")
public class KycStatusController {

    private final GetKycStatusUseCase getKycStatusUseCase;
    private final GatewayRequestVerifier gatewayRequestVerifier;

    public KycStatusController(GetKycStatusUseCase getKycStatusUseCase, GatewayRequestVerifier gatewayRequestVerifier) {
        this.getKycStatusUseCase = getKycStatusUseCase;
        this.gatewayRequestVerifier = gatewayRequestVerifier;
    }

    @GetMapping("/status")
    public ResponseEntity<KycStatusResponse> getKycStatus(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-Internal-Secret", required = false) String gatewaySecret) {
        gatewayRequestVerifier.verify(gatewaySecret);
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Caller identity header (X-User-Id) is missing");
        }
        UUID userId;
        try {
            userId = UUID.fromString(userIdHeader.trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid caller identity header format");
        }

        KycStatusResponse response = getKycStatusUseCase.getStatus(userId);
        return ResponseEntity.ok(response);
    }
}
