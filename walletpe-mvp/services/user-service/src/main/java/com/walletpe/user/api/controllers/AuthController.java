package com.walletpe.user.api.controllers;

import com.walletpe.user.api.dto.ApiResponse;
import com.walletpe.user.api.dto.AuthResponse;
import com.walletpe.user.api.dto.ForgotPasswordRequest;
import com.walletpe.user.api.dto.LoginRequest;
import com.walletpe.user.api.dto.ResetPasswordRequest;
import com.walletpe.user.application.usecases.LoginUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final LoginUseCase loginUseCase;

    public AuthController(LoginUseCase loginUseCase) {
        this.loginUseCase = loginUseCase;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = loginUseCase.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        ApiResponse<String> response = loginUseCase.forgotPassword(request.getEmail());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        ApiResponse<String> response = loginUseCase.resetPassword(request);
        return ResponseEntity.ok(response);
    }
}
