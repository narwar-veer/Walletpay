package com.walletpe.user.api.controllers;

import com.walletpe.user.api.dto.ApiResponse;
import com.walletpe.user.api.dto.VerifyMobileRequest;
import com.walletpe.user.application.usecases.VerifyMobileOtpUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/mobile-verification")
public class ContactVerificationController {

    private final VerifyMobileOtpUseCase verifyMobileOtpUseCase;
    public ContactVerificationController(VerifyMobileOtpUseCase verifyMobileOtpUseCase) {
        this.verifyMobileOtpUseCase = verifyMobileOtpUseCase;
    }

    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<String>> initiate(@RequestHeader("X-User-Id") UUID userId) {
        ApiResponse<String> response = verifyMobileOtpUseCase.initiateMobileVerification(userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<String>> verify(@RequestHeader("X-User-Id") UUID userId,
                                                      @Valid @RequestBody VerifyMobileRequest request) {
        ApiResponse<String> response = verifyMobileOtpUseCase.verifyMobile(userId, request.getOtp());
        return ResponseEntity.ok(response);
    }
}
