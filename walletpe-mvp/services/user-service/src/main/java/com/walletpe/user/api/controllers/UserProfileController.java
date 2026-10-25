package com.walletpe.user.api.controllers;

import com.walletpe.user.api.dto.ApiResponse;
import com.walletpe.user.api.dto.UpdateProfileRequest;
import com.walletpe.user.api.dto.UserResponse;
import com.walletpe.user.application.usecases.UserProfileUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserProfileController {

    private final UserProfileUseCase userProfileUseCase;
    public UserProfileController(UserProfileUseCase userProfileUseCase) {
        this.userProfileUseCase = userProfileUseCase;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(@RequestHeader("X-User-Id") UUID userId) {
        UserResponse response = userProfileUseCase.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.success("Profile retrieved successfully", response));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateMyProfile(@RequestHeader("X-User-Id") UUID userId,
                                                                    @Valid @RequestBody UpdateProfileRequest request) {
        UserResponse response = userProfileUseCase.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", response));
    }

    @PostMapping("/me/verify-email-change")
    public ResponseEntity<ApiResponse<String>> verifyEmailChange(@RequestHeader("X-User-Id") UUID userId,
                                                                 @RequestParam String otp) {
        ApiResponse<String> response = userProfileUseCase.verifyEmailChange(userId, otp);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/me/verify-mobile-change")
    public ResponseEntity<ApiResponse<String>> verifyMobileChange(@RequestHeader("X-User-Id") UUID userId,
                                                                   @RequestParam String otp) {
        return ResponseEntity.ok(userProfileUseCase.verifyMobileChange(userId, otp));
    }
}
