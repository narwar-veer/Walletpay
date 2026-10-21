package com.walletpe.user.api.controllers;

import com.walletpe.user.api.dto.ApiResponse;
import com.walletpe.user.api.dto.VerifyMobileRequest;
import com.walletpe.user.application.usecases.VerifyMobileOtpUseCase;
import com.walletpe.user.infrastructure.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/mobile-verification")
public class ContactVerificationController {

    private final VerifyMobileOtpUseCase verifyMobileOtpUseCase;
    private final JwtTokenProvider jwtTokenProvider;

    public ContactVerificationController(VerifyMobileOtpUseCase verifyMobileOtpUseCase,
                                         JwtTokenProvider jwtTokenProvider) {
        this.verifyMobileOtpUseCase = verifyMobileOtpUseCase;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    private UUID extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        Claims claims = jwtTokenProvider.getClaims(token);
        return UUID.fromString(claims.getSubject());
    }

    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<String>> initiate(@RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        ApiResponse<String> response = verifyMobileOtpUseCase.initiateMobileVerification(userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<String>> verify(@RequestHeader("Authorization") String authHeader,
                                                      @Valid @RequestBody VerifyMobileRequest request) {
        UUID userId = extractUserId(authHeader);
        ApiResponse<String> response = verifyMobileOtpUseCase.verifyMobile(userId, request.getOtp());
        return ResponseEntity.ok(response);
    }
}
