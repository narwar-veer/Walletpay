package com.walletpe.user.api.controllers;

import com.walletpe.user.api.dto.ApiResponse;
import com.walletpe.user.api.dto.ResendEmailOtpRequest;
import com.walletpe.user.api.dto.SignupRequest;
import com.walletpe.user.api.dto.UserResponse;
import com.walletpe.user.api.dto.VerifyEmailRequest;
import com.walletpe.user.application.usecases.RegisterUserUseCase;
import com.walletpe.user.application.usecases.VerifyEmailOtpUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class RegistrationController {

    private final RegisterUserUseCase registerUserUseCase;
    private final VerifyEmailOtpUseCase verifyEmailOtpUseCase;

    public RegistrationController(RegisterUserUseCase registerUserUseCase,
                                  VerifyEmailOtpUseCase verifyEmailOtpUseCase) {
        this.registerUserUseCase = registerUserUseCase;
        this.verifyEmailOtpUseCase = verifyEmailOtpUseCase;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponse>> signup(@Valid @RequestBody SignupRequest request) {
        UserResponse response = registerUserUseCase.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully. Please verify your email with the OTP sent.", response));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<String>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        ApiResponse<String> response = verifyEmailOtpUseCase.verifyEmail(request.getEmail(), request.getOtp());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-email-otp")
    public ResponseEntity<ApiResponse<String>> resendEmailOtp(@Valid @RequestBody ResendEmailOtpRequest request) {
        ApiResponse<String> response = verifyEmailOtpUseCase.resendEmailOtp(request.getEmail());
        return ResponseEntity.ok(response);
    }
}
