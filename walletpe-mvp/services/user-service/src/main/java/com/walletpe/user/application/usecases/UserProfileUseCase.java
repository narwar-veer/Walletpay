package com.walletpe.user.application.usecases;

import com.walletpe.user.api.dto.ApiResponse;
import com.walletpe.user.api.dto.UpdateProfileRequest;
import com.walletpe.user.api.dto.UserResponse;
import com.walletpe.user.application.mapper.UserMapper;
import com.walletpe.user.application.service.OtpService;
import com.walletpe.user.domain.enums.OtpPurpose;
import com.walletpe.user.domain.models.User;
import com.walletpe.user.infrastructure.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserProfileUseCase {

    private final UserRepository userRepository;
    private final OtpService otpService;

    public UserProfileUseCase(UserRepository userRepository, OtpService otpService) {
        this.userRepository = userRepository;
        this.otpService = otpService;
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return UserMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Name update
        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            user.setName(request.getName().trim());
        }

        // Email change request: do not overwrite active email without verification
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            String newEmail = request.getEmail().trim().toLowerCase();
            if (!newEmail.equals(user.getEmail())) {
                if (userRepository.existsByEmail(newEmail)) {
                    throw new IllegalArgumentException("Email already in use by another account.");
                }
                user.setPendingEmail(newEmail);
                // Dispatch verification for new email
                otpService.createChallenge(user, newEmail, OtpPurpose.EMAIL_VERIFICATION, "EMAIL");
            }
        }

        // Mobile change request: do not overwrite without verification
        if (request.getMobileNumber() != null && !request.getMobileNumber().trim().isEmpty()) {
            String newMobile = request.getMobileNumber().trim();
            if (!newMobile.equals(user.getMobileNumber())) {
                if (userRepository.existsByMobileNumber(newMobile)) {
                    throw new IllegalArgumentException("Mobile number already in use by another account.");
                }
                user.setPendingMobileNumber(newMobile);
                // Dispatch verification for new mobile
                otpService.createChallenge(user, newMobile, OtpPurpose.MOBILE_VERIFICATION, "SMS");
            }
        }

        User updatedUser = userRepository.save(user);
        return UserMapper.toResponse(updatedUser);
    }

    @Transactional
    public ApiResponse<String> verifyEmailChange(UUID userId, String otp) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getPendingEmail() == null) {
            throw new IllegalStateException("No email change pending verification.");
        }

        boolean verified = otpService.verifyOtp(user.getPendingEmail(), OtpPurpose.EMAIL_VERIFICATION, otp);
        if (!verified) {
            throw new IllegalArgumentException("Invalid OTP for pending email verification.");
        }

        user.setEmail(user.getPendingEmail());
        user.setPendingEmail(null);
        user.setEmailVerified(true);
        userRepository.save(user);

        return ApiResponse.success("Email changed and verified successfully.", null);
    }
}
