package com.walletpe.user.api.controllers;

import com.walletpe.user.api.dto.ApiResponse;
import com.walletpe.user.api.dto.UpdateProfileRequest;
import com.walletpe.user.api.dto.UserResponse;
import com.walletpe.user.application.usecases.UserProfileUseCase;
import com.walletpe.user.infrastructure.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserProfileController {

    private final UserProfileUseCase userProfileUseCase;
    private final JwtTokenProvider jwtTokenProvider;

    public UserProfileController(UserProfileUseCase userProfileUseCase,
                                 JwtTokenProvider jwtTokenProvider) {
        this.userProfileUseCase = userProfileUseCase;
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

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(@RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        UserResponse response = userProfileUseCase.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.success("Profile retrieved successfully", response));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateMyProfile(@RequestHeader("Authorization") String authHeader,
                                                                    @Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = extractUserId(authHeader);
        UserResponse response = userProfileUseCase.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", response));
    }

    @PostMapping("/me/verify-email-change")
    public ResponseEntity<ApiResponse<String>> verifyEmailChange(@RequestHeader("Authorization") String authHeader,
                                                                 @RequestParam String otp) {
        UUID userId = extractUserId(authHeader);
        ApiResponse<String> response = userProfileUseCase.verifyEmailChange(userId, otp);
        return ResponseEntity.ok(response);
    }
}
