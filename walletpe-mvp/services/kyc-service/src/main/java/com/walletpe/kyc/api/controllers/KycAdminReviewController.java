package com.walletpe.kyc.api.controllers;

import com.walletpe.kyc.api.dto.KycHoldRequest;
import com.walletpe.kyc.api.dto.KycReviewRequest;
import com.walletpe.kyc.api.dto.KycStatusResponse;
import com.walletpe.kyc.application.usecases.PutKycOnHoldUseCase;
import com.walletpe.kyc.application.usecases.ReviewKycUseCase;
import com.walletpe.kyc.api.security.GatewayRequestVerifier;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/kyc")
public class KycAdminReviewController {

    private final ReviewKycUseCase reviewKycUseCase;
    private final PutKycOnHoldUseCase putKycOnHoldUseCase;
    private final GatewayRequestVerifier gatewayRequestVerifier;

    public KycAdminReviewController(ReviewKycUseCase reviewKycUseCase,
                                    PutKycOnHoldUseCase putKycOnHoldUseCase,
                                    GatewayRequestVerifier gatewayRequestVerifier) {
        this.reviewKycUseCase = reviewKycUseCase;
        this.putKycOnHoldUseCase = putKycOnHoldUseCase;
        this.gatewayRequestVerifier = gatewayRequestVerifier;
    }

    @PostMapping("/{kycId}/review")
    public ResponseEntity<KycStatusResponse> reviewKyc(
            @PathVariable UUID kycId,
            @RequestHeader(value = "X-User-Id", required = false) String adminIdHeader,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader,
            @RequestHeader(value = "X-Internal-Secret", required = false) String gatewaySecret,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody KycReviewRequest request) {
        gatewayRequestVerifier.verify(gatewaySecret);
        UUID adminId = parseAdminId(adminIdHeader);
        checkAdminRole(rolesHeader);

        KycStatusResponse response = reviewKycUseCase.review(adminId, kycId, request, correlationId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{kycId}/hold")
    public ResponseEntity<KycStatusResponse> holdKyc(
            @PathVariable UUID kycId,
            @RequestHeader(value = "X-User-Id", required = false) String adminIdHeader,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader,
            @RequestHeader(value = "X-Internal-Secret", required = false) String gatewaySecret,
            @RequestBody(required = false) KycHoldRequest request) {
        gatewayRequestVerifier.verify(gatewaySecret);
        UUID adminId = parseAdminId(adminIdHeader);
        checkAdminRole(rolesHeader);

        KycStatusResponse response = putKycOnHoldUseCase.hold(adminId, kycId, request);
        return ResponseEntity.ok(response);
    }

    private UUID parseAdminId(String adminIdHeader) {
        if (adminIdHeader == null || adminIdHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Caller identity header (X-User-Id) is missing");
        }
        try {
            return UUID.fromString(adminIdHeader.trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin identity header format");
        }
    }

    private void checkAdminRole(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: missing role header");
        }
        String[] roles = rolesHeader.split(",");
        boolean isAdmin = false;
        for (String r : roles) {
            String clean = r.trim().toUpperCase();
            if ("ADMIN".equals(clean) || "ROLE_ADMIN".equals(clean) ||
                "SUPER_ADMIN".equals(clean) || "ROLE_SUPER_ADMIN".equals(clean)) {
                isAdmin = true;
                break;
            }
        }
        if (!isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: administrator privileges required");
        }
    }
}
